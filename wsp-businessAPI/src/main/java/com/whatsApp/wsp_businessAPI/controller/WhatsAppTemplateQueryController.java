package com.whatsApp.wsp_businessAPI.controller;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;
import com.whatsApp.wsp_businessAPI.service.CurrentTenantService;
import com.whatsApp.wsp_businessAPI.service.MetaApiClient;
import com.whatsApp.wsp_businessAPI.service.WhatsAppTemplateService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/whatsapp/templates")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateQueryController {

    private final WhatsAppTemplateRepository templateRepo;
    private final WhatsAppAccountRepository accountRepo;
    private final CurrentTenantService currentTenantService;
    private final WhatsAppTemplateService templateService;
    private final MetaApiClient metaApiClient;

    /**
     * Get all templates for current tenant
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getTemplates() {

        long startTime = System.currentTimeMillis();
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching templates for tenant: {}", tenantId);

        Optional<WhatsAppAccount> accountOpt = accountRepo.findByTenantId(tenantId);
        
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.notFound("No WhatsApp account connected for this tenant"));
        }

        String wabaId = accountOpt.get().getWabaId();
        log.info("Found WABA ID: {} for tenant: {}", wabaId, tenantId);

        List<WhatsAppTemplate> templates = templateRepo.findByTenantIdAndWabaId(tenantId, wabaId);

        if (templates == null || templates.isEmpty()) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("No templates found in {} ms", elapsedTime);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.notFound("No templates found for this WhatsApp account"));
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Returning {} templates for tenant: {} in {} ms", templates.size(), tenantId, elapsedTime);
        
        return ResponseEntity.ok(
                ApiResponse.success("Templates fetched successfully", templates)
        );
    }

    /**
     * Get template(s) by name
     * - Single: /name/registerpro_promo_v1
     * - Multiple: /name/registerpro_promo_v1,quantum_register_free_offer
     */
    @GetMapping("/name/{templateNames}")
    public ResponseEntity<ApiResponse<?>> getTemplateByName(@PathVariable String templateNames) {

        long startTime = System.currentTimeMillis();
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching templates for names: {} for tenant: {}", templateNames, tenantId);

        Optional<WhatsAppAccount> accountOpt = accountRepo.findByTenantId(tenantId);
        
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.notFound("No WhatsApp account connected for this tenant"));
        }

        String wabaId = accountOpt.get().getWabaId();
        
        // Split by comma to handle multiple names
        List<String> nameList = new ArrayList<>();
        for (String name : templateNames.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                nameList.add(trimmed);
            }
        }

        // Fetch all templates for this WABA
        List<WhatsAppTemplate> allTemplates = templateRepo.findByTenantIdAndWabaId(tenantId, wabaId);
        
        // Create map for quick lookup
        Map<String, WhatsAppTemplate> templateMap = allTemplates.stream()
            .collect(Collectors.toMap(
                WhatsAppTemplate::getName,
                template -> template,
                (existing, replacement) -> existing
            ));

        List<WhatsAppTemplate> foundTemplates = new ArrayList<>();
        List<String> notFoundTemplates = new ArrayList<>();

        for (String name : nameList) {
            WhatsAppTemplate template = templateMap.get(name);
            if (template != null) {
                // Verify tenant ownership
                if (template.getTenantId().equals(tenantId)) {
                    foundTemplates.add(template);
                } else {
                    notFoundTemplates.add(name);
                }
            } else {
                notFoundTemplates.add(name);
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Found {} templates in {} ms, {} not found", foundTemplates.size(), elapsedTime, notFoundTemplates.size());

        // If only one template requested and found, return single object for backward compatibility
        if (nameList.size() == 1 && foundTemplates.size() == 1) {
            return ResponseEntity.ok(
                    ApiResponse.success("Template fetched successfully", foundTemplates.get(0))
            );
        }

        // For multiple templates, return list with summary
        Map<String, Object> response = new HashMap<>();
        response.put("templates", foundTemplates);
        response.put("totalFound", foundTemplates.size());
        response.put("totalRequested", nameList.size());
        response.put("notFound", notFoundTemplates);
        
        return ResponseEntity.ok(
                ApiResponse.success("Templates fetched successfully", response)
        );
    }

    /**
     * Get template by ID
     */
    @GetMapping("/id/{templateId}")
    public ResponseEntity<ApiResponse<?>> getTemplateById(@PathVariable Long templateId) {

        long startTime = System.currentTimeMillis();
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching template by ID: {} for tenant: {}", templateId, tenantId);

        Optional<WhatsAppTemplate> templateOpt = templateRepo.findById(templateId);
        
        if (templateOpt.isEmpty()) {
            throw new ValidationException(
                "TEMPLATE_NOT_FOUND",
                "Template not found with ID: " + templateId
            );
        }

        WhatsAppTemplate template = templateOpt.get();

        if (!template.getTenantId().equals(tenantId)) {
            throw new ValidationException(
                "UNAUTHORIZED",
                "You don't have permission to access this template"
            );
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Template ID {} fetched in {} ms", templateId, elapsedTime);

        return ResponseEntity.ok(
                ApiResponse.success("Template fetched successfully", template)
        );
    }

    /**
     * Search templates with pagination
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<?>> searchTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        long startTime = System.currentTimeMillis();
        Long tenantId = currentTenantService.getTenantId();
        log.info("Searching templates with keyword: {}, page: {}, size: {}", keyword, page, size);

        Optional<WhatsAppAccount> accountOpt = accountRepo.findByTenantId(tenantId);
        
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.notFound("No WhatsApp account connected for this tenant"));
        }

        String wabaId = accountOpt.get().getWabaId();

        List<WhatsAppTemplate> allTemplates = templateRepo.findByTenantIdAndWabaId(tenantId, wabaId);
        
        // Filter by keyword if provided
        List<WhatsAppTemplate> filteredTemplates = allTemplates;
        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKeyword = keyword.toLowerCase().trim();
            filteredTemplates = allTemplates.stream()
                .filter(t -> t.getName().toLowerCase().contains(searchKeyword) ||
                             t.getCategory().name().toLowerCase().contains(searchKeyword) ||
                             t.getStatus().name().toLowerCase().contains(searchKeyword))
                .collect(Collectors.toList());
        }

        // Pagination
        int start = page * size;
        int end = Math.min(start + size, filteredTemplates.size());
        List<WhatsAppTemplate> paginatedTemplates = filteredTemplates.subList(start, end);

        Map<String, Object> response = new HashMap<>();
        response.put("templates", paginatedTemplates);
        response.put("totalCount", filteredTemplates.size());
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (int) Math.ceil((double) filteredTemplates.size() / size));

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Search returned {} templates in {} ms", filteredTemplates.size(), elapsedTime);

        return ResponseEntity.ok(
                ApiResponse.success("Templates searched successfully", response)
        );
    }

    /**
     * Get media URL for template image
     */
    @GetMapping("/media/{mediaId}")
    public ResponseEntity<ApiResponse<?>> getMediaUrl(@PathVariable String mediaId) {
        
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching media URL for mediaId: {}, tenant: {}", mediaId, tenantId);
        
        Optional<WhatsAppAccount> accountOpt = accountRepo.findByTenantId(tenantId);
        
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.notFound("No WhatsApp account connected"));
        }
        
        String token = accountOpt.get().getSystemUserToken();
        
        Map<String, Object> mediaInfo = metaApiClient.getMediaInfo(mediaId, token);
        
        if (mediaInfo.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "Failed to fetch media", "MEDIA_ERROR", mediaInfo.get("error").toString()));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("mediaId", mediaId);
        response.put("url", mediaInfo.get("url"));
        response.put("mimeType", mediaInfo.get("mime_type"));
        response.put("fileSize", mediaInfo.get("file_size"));
        
        return ResponseEntity.ok(
                ApiResponse.success("Media URL fetched", response)
        );
    }

    @DeleteMapping("/delete/{templateId}")
    public ResponseEntity<ApiResponse<?>> deleteTemplate(@PathVariable Long templateId) {

        Long tenantId = currentTenantService.getTenantId();
        log.info("Delete request for template ID: {} by tenant: {}", templateId, tenantId);

        try {
            templateService.deleteTemplate(templateId, tenantId);
            
            return ResponseEntity.ok(
                    ApiResponse.success("Template deleted successfully", null)
            );

        } catch (ValidationException e) {
            log.warn("Validation error deleting template: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                        400,
                        e.getMessage(),
                        e.getErrorCode(),
                        "Cannot delete this template"
                    ));
        } catch (Exception e) {
            log.error("Error deleting template ID: {}", templateId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.internalError("Failed to delete template", e.getMessage()));
        }
    }
}