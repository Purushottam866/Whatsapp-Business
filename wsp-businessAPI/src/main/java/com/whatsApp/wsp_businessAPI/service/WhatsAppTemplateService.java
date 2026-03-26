package com.whatsApp.wsp_businessAPI.service;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.whatsApp.wsp_businessAPI.dto.CreateTemplateRequest;
import com.whatsApp.wsp_businessAPI.entity.TemplateImage;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.exceptions.ResourceAlreadyExistsException;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.TemplateImageRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateService {

    private final WhatsAppTemplateRepository repository;
    private final WhatsAppAccountRepository accountRepo;
    private final CurrentTenantService currentTenantService;
    private final ObjectMapper objectMapper;
    private final MetaApiClient metaApiClient;
    private final TemplateImageRepository templateImageRepository;

    @Transactional
    public WhatsAppTemplate createTemplate(CreateTemplateRequest req, MultipartFile file) {

        Long tenantId = currentTenantService.getTenantId();

        WhatsAppAccount account = accountRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new ValidationException(
                        "WhatsApp not connected",
                        "Please connect WhatsApp before creating templates"
                ));

        String wabaId = account.getWabaId();

        repository.findByWabaIdAndNameAndLanguage(wabaId, req.getName(), req.getLanguage())
                .ifPresent(t -> {
                    throw new ResourceAlreadyExistsException(
                            "Template name already exists in this WhatsApp account"
                    );
                });

        /* ================= TWO UPLOADS FOR IMAGE TEMPLATES ================= */
        String uploadedHeaderHandle = null;
        String uploadedMediaId = null;  // REAL media ID for sending
        byte[] imageBytes = null;
        String imageContentType = null;
        String imageFileName = null;
        
        try {
            JsonNode components = objectMapper.valueToTree(req.getComponents());

            for (int i = 0; i < components.size(); i++) {
                JsonNode comp = components.get(i);
                String type = comp.has("type") ? comp.get("type").asText() : "";

                if ("HEADER".equalsIgnoreCase(type) 
                        && comp.has("format") 
                        && "IMAGE".equalsIgnoreCase(comp.get("format").asText())) {

                    if (file == null || file.isEmpty()) {
                        throw new ValidationException(
                                "Header image required",
                                "You selected IMAGE header but no file uploaded"
                        );
                    }

                    // Store image bytes for backup
                    imageBytes = file.getBytes();
                    imageContentType = file.getContentType();
                    imageFileName = file.getOriginalFilename();

                    // 🔥 UPLOAD 1: For template preview (resumable) - gets header_handle
                    Map<String, String> previewUpload = metaApiClient.uploadMediaForTemplate(
                            account.getPhoneNumberId(),
                            account.getSystemUserToken(),
                            file
                    );
                    uploadedHeaderHandle = previewUpload.get("headerHandle");
                    
                    log.info("✅ Header Handle received: {}", uploadedHeaderHandle);

                    // 🔥 UPLOAD 2: For sending (regular media) - gets REAL media ID
                    uploadedMediaId = metaApiClient.uploadMedia(
                            account.getPhoneNumberId(),
                            account.getSystemUserToken(),
                            file
                    );
                    
                    log.info("✅✅ REAL MEDIA ID received: {} (length: {})", 
                             uploadedMediaId, uploadedMediaId.length());

                    // Add example with header_handle for template preview
                    ObjectNode headerComp = (ObjectNode) comp;
                    ObjectNode example = objectMapper.createObjectNode();
                    ArrayNode headerHandleArray = example.putArray("header_handle");
                    headerHandleArray.add(uploadedHeaderHandle);
                    headerComp.set("example", example);
                    
                    break;
                }
            }

            req.setComponents(components);

        } catch (IOException e) {
            log.error("Failed to read image file", e);
            throw new RuntimeException("Failed to read image file: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed processing header image", e);
            throw new RuntimeException("Failed processing header image: " + e.getMessage(), e);
        }

        /* ================= SAVE TEMPLATE WITH REAL MEDIA ID ================= */
        boolean hasParams = detectParameters(req.getComponents());

        WhatsAppTemplate template = WhatsAppTemplate.builder()
                .tenantId(tenantId)
                .wabaId(wabaId)
                .name(req.getName())
                .category(req.getCategory())
                .language(req.getLanguage())
                .componentsJson(req.getComponents().toString())
                .hasParameters(hasParams)
                .status(WhatsAppTemplate.Status.DRAFT)
                .headerHandle(uploadedHeaderHandle)
                .headerMediaId(uploadedMediaId)  // ✅ REAL media ID saved immediately
                .build();

        WhatsAppTemplate saved = repository.save(template);

        /* ================= SAVE IMAGE TO DATABASE (BACKUP) ================= */
        if (imageBytes != null) {
            TemplateImage templateImage = TemplateImage.builder()
                    .template(saved)
                    .imageData(imageBytes)
                    .contentType(imageContentType)
                    .fileName(imageFileName)
                    .fileSize((long) imageBytes.length)
                    .build();
            
            templateImageRepository.save(templateImage);
            saved.setTemplateImage(templateImage);
            
            log.info("✅ Image saved to database as backup for template: {}", saved.getName());
        }

        log.info("========== TEMPLATE CREATION SUMMARY ==========");
        log.info("📋 Template ID: {}, Name: {}", saved.getId(), saved.getName());
        log.info("📎 Header Handle: {}", saved.getHeaderHandle());
        log.info("✅✅ REAL MEDIA ID STORED: {} (length: {})", 
                 saved.getHeaderMediaId(), 
                 saved.getHeaderMediaId() != null ? saved.getHeaderMediaId().length() : 0);
        log.info("🎯 This template will work on WEB immediately after approval!");
        log.info("================================================");

        return saved;
    }

    private boolean detectParameters(JsonNode components) {
        Pattern pattern = Pattern.compile("\\{\\{\\d+}}");
        try {
            for (JsonNode comp : components) {
                String type = comp.has("type") ? comp.get("type").asText() : "";
                if ("BODY".equalsIgnoreCase(type)) {
                    JsonNode textNode = comp.get("text");
                    if (textNode != null && pattern.matcher(textNode.asText()).find()) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to inspect template parameters", e);
        }
        return false;
    }

    /**
     * ✅ DELETE TEMPLATE - Only for DRAFT or REJECTED templates
     * Also deletes associated image from template_images table
     */
    @Transactional
    public void deleteTemplate(Long templateId, Long tenantId) {
        
        // Verify template exists and belongs to tenant
        WhatsAppTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new ValidationException(
                        "Template not found",
                        "No template found with ID: " + templateId
                ));

        // Verify tenant ownership
        if (!template.getTenantId().equals(tenantId)) {
            throw new ValidationException(
                    "Unauthorized",
                    "You don't have permission to delete this template"
            );
        }

        // Check if template can be deleted (only DRAFT or REJECTED)
        if (template.getStatus() != WhatsAppTemplate.Status.DRAFT && 
            template.getStatus() != WhatsAppTemplate.Status.REJECTED) {
            
            throw new ValidationException(
                    "Cannot delete template",
                    "Only DRAFT or REJECTED templates can be deleted. Current status: " + template.getStatus()
            );
        }

        log.info("Deleting template ID: {} with status: {}", templateId, template.getStatus());

        // First, check if there's an associated image and delete it
        TemplateImage templateImage = templateImageRepository.findByTemplateId(templateId).orElse(null);
        
        if (templateImage != null) {
            log.info("Deleting associated image for template ID: {}", templateId);
            templateImageRepository.delete(templateImage);
        }

        // Then delete the template
        repository.deleteById(templateId);
        
        log.info("✅ Template ID: {} and associated image deleted successfully", templateId);
    }
}