package com.whatsApp.wsp_businessAPI.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.dto.BulkSendWhatsAppMessageRequest;
import com.whatsApp.wsp_businessAPI.dto.CampaignStatusResponse;
import com.whatsApp.wsp_businessAPI.dto.SendWhatsAppMessageRequest; 
import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppCampaign; 
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.exceptions.WhatsAppApiException;
import com.whatsApp.wsp_businessAPI.repository.TemplateImageRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppCampaignRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppMessageService {

    private final WhatsAppTemplateRepository templateRepo;
    private final WhatsAppAccountRepository accountRepo;
    private final WhatsAppMessageRepository messageRepo;
    private final MetaApiClient metaApiClient;
    private final WhatsAppTokenService tokenService;
    private final CurrentTenantService currentTenantService;
    private final WhatsAppCampaignRepository campaignRepository;
    private final TemplateImageRepository templateImageRepository;
    private final ObjectMapper objectMapper;
    private final WhatsAppTemplateStatusSyncService templateStatusSyncService;

    @Transactional
    public ApiResponse<?> sendSingle(SendWhatsAppMessageRequest req) {

        WhatsAppTemplate template = templateRepo.findByWabaIdAndNameAndLanguage(
                req.getWabaId(),
                req.getTemplateName(),
                req.getLanguage()
        ).orElseThrow(() -> new ValidationException(
                "Template not found",
                "Invalid template name or language"
        ));

        if (template.getStatus() != WhatsAppTemplate.Status.APPROVED) {
            throw new ValidationException(
                    "Template not approved",
                    "Current status: " + template.getStatus()
            );
        }

        Long tenantId = currentTenantService.getTenantId();
        
        // ✅ Get the appropriate WhatsApp account (specific or default)
        WhatsAppAccount account = getWhatsAppAccount(tenantId, req);
        String validToken = requireValidToken(account);
        String senderPhoneNumberId = account.getPhoneNumberId();

        log.info("Sending message from phone number ID: {} ({})", 
                 senderPhoneNumberId, account.getDisplayPhoneNumber());

        Map<String, Object> parameters = buildParameters(template, req.getParameters());

        // Save as QUEUED initially
        WhatsAppMessage msg = WhatsAppMessage.builder()
                .tenantId(tenantId)
                .wabaId(req.getWabaId())
                .phoneNumberId(senderPhoneNumberId)
                .phoneNumber(req.getTo())
                .templateName(req.getTemplateName())
                .templateLanguage(req.getLanguage())
                .templateCategory(template.getCategory().name())
                .templateParametersJson(parameters != null ? parameters.toString() : null)
                .status(WhatsAppMessage.Status.QUEUED)
                .build();

        messageRepo.save(msg);

        try {
            log.info("Sending template message to Meta for phone: {} using sender: {}", 
                     req.getTo(), senderPhoneNumberId);
            
            // Send to Meta
            Map<String, Object> metaResp = metaApiClient.sendTemplateMessage(
                    senderPhoneNumberId,
                    validToken,
                    req.getTo(),
                    req.getTemplateName(),
                    req.getLanguage(),
                    parameters
            );

            // Check if there's an error in the response
            if (metaResp.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) metaResp.get("error");
                String errorMessage = (String) error.get("message");
                Integer errorCode = (Integer) error.get("code");
                
                log.error("❌ Meta returned error: {} - {}", errorCode, errorMessage);
                
                msg.setStatus(WhatsAppMessage.Status.FAILED);
                msg.setFailureReason("Meta error: " + errorCode + " - " + errorMessage);
                messageRepo.save(msg);
                
                return ApiResponse.error(
                        502,
                        "Message failed: " + errorMessage,
                        "META_ERROR_" + errorCode,
                        "Message was rejected by Meta"
                );
            }

            // Check for messages array (success case)
            if (metaResp.containsKey("messages")) {
                List<Map<String, Object>> messages = (List<Map<String, Object>>) metaResp.get("messages");
                if (messages != null && !messages.isEmpty()) {
                    String metaMessageId = messages.get(0).get("id").toString();
                    
                    msg.setMetaMessageId(metaMessageId);
                    msg.setStatus(WhatsAppMessage.Status.PENDING);
                    messageRepo.save(msg);
                    
                    templateStatusSyncService.manuallyResetQueueCircuit();
                    log.info("✅ Template message accepted by Meta with ID: {} from sender: {}", 
                             metaMessageId, senderPhoneNumberId);
                    
                    return ApiResponse.success("Template message accepted by Meta", Map.of(
                            "messageId", msg.getId(), 
                            "metaMessageId", metaMessageId,
                            "status", "PENDING",
                            "sender", account.getDisplayPhoneNumber(),
                            "note", "Final delivery status will be sent via webhook"
                    ));
                }
            }
            
            // If we get here, something unexpected happened
            msg.setStatus(WhatsAppMessage.Status.FAILED);
            msg.setFailureReason("Unexpected Meta response: " + metaResp);
            messageRepo.save(msg);
            
            return ApiResponse.error(
                    502,
                    "Message failed: Unexpected response from Meta",
                    "META_UNEXPECTED_RESPONSE",
                    metaResp.toString()
            );
            
        } catch (WhatsAppApiException e) {
            log.error("❌ WhatsApp API Exception: {}", e.getMessage());
            msg.setStatus(WhatsAppMessage.Status.FAILED);
            msg.setFailureReason(e.getMessage());
            messageRepo.save(msg);
            
            return ApiResponse.error(
                    502,
                    "Message failed: " + e.getMessage(),
                    e.getErrorCode(),
                    e.getMessage()
            );
            
        } catch (Exception e) {
            log.error("❌ Unexpected error sending message: {}", e.getMessage());
            msg.setStatus(WhatsAppMessage.Status.FAILED);
            msg.setFailureReason("System error: " + e.getMessage());
            messageRepo.save(msg);
            
            throw new WhatsAppApiException("Failed to send message: " + e.getMessage(), "SYSTEM_ERROR");
        }
    }

    // ✅ NEW: Helper method to get WhatsApp account based on request
    private WhatsAppAccount getWhatsAppAccount(Long tenantId, SendWhatsAppMessageRequest req) {
        if (req.getPhoneNumberId() != null && !req.getPhoneNumberId().isEmpty()) {
            // Use specified phone number
            return tokenService.validateAndGetAccountByPhoneNumberId(
                    tenantId, req.getWabaId(), req.getPhoneNumberId());
        } else {
            // Use default account
            return tokenService.validateAndGetActiveAccount(tenantId, req.getWabaId());
        }
    }

    private Map<String, Object> buildParameters(
        WhatsAppTemplate template,
        Map<String, Object> requestParams
    ) {
        Map<String, Object> finalParams = new HashMap<>();

        if (requestParams != null) {
            finalParams.putAll(requestParams);
        }

        if (template.hasImageHeader() && template.getHeaderMediaId() != null) {
            log.info("✅ Using stored media ID for template {}: {}", 
                     template.getName(), template.getHeaderMediaId());
            
            List<Map<String, Object>> headerParams = new ArrayList<>();
            Map<String, Object> imageParam = new HashMap<>();
            imageParam.put("type", "image");
            
            Map<String, Object> image = new HashMap<>();
            image.put("id", template.getHeaderMediaId());
            imageParam.put("image", image);
            
            headerParams.add(imageParam);
            
            List<Map<String, Object>> components = new ArrayList<>();
            Map<String, Object> headerComponent = new HashMap<>();
            headerComponent.put("type", "header");
            headerComponent.put("parameters", headerParams);
            components.add(headerComponent);
            
            finalParams.put("components", components);
        }

        return finalParams;
    }

    private String requireValidToken(WhatsAppAccount account) {
        if (account.getSystemUserToken() == null || account.getSystemUserToken().isBlank()) {
            throw new WhatsAppApiException("Missing WhatsApp access token", "META_TOKEN_MISSING");
        }
        return account.getSystemUserToken();
    }

    @Transactional
    public ApiResponse<?> sendBulk(BulkSendWhatsAppMessageRequest req) {
        WhatsAppTemplate template = templateRepo.findByWabaIdAndNameAndLanguage(
                req.getWabaId(), req.getTemplateName(), req.getLanguage()
        ).orElseThrow(() -> new ValidationException("Template not found", "Invalid template name or language"));

        if (template.getStatus() != WhatsAppTemplate.Status.APPROVED) {
            throw new ValidationException("Template not approved", "Current status: " + template.getStatus());
        }

        Long tenantId = currentTenantService.getTenantId();
        
        // ✅ Get the appropriate WhatsApp account for bulk
        WhatsAppAccount account = getWhatsAppAccountForBulk(tenantId, req);
        String senderPhoneNumberId = account.getPhoneNumberId();

        log.info("Sending bulk messages from phone number ID: {} ({})", 
                 senderPhoneNumberId, account.getDisplayPhoneNumber());

        WhatsAppCampaign campaign = WhatsAppCampaign.builder()
                .tenantId(tenantId)
                .wabaId(req.getWabaId())
                .templateName(req.getTemplateName())
                .templateLanguage(req.getLanguage())
                .totalContacts(req.getRecipients().size())
                .status(WhatsAppCampaign.Status.CREATED)
                .build();

        campaignRepository.save(campaign);

        Map<String, Object> parameters = buildParameters(template, req.getParameters());
        String paramsJson = null;
        try {
            paramsJson = objectMapper.writeValueAsString(parameters);
        } catch (Exception e) {
            log.warn("Failed to serialize parameters", e);
        }

        for (String recipient : req.getRecipients()) {
            WhatsAppMessage msg = WhatsAppMessage.builder()
                    .tenantId(tenantId)
                    .campaignId(campaign.getId())
                    .wabaId(req.getWabaId())
                    .phoneNumberId(senderPhoneNumberId)
                    .phoneNumber(recipient)
                    .templateName(req.getTemplateName())
                    .templateLanguage(req.getLanguage())
                    .templateCategory(template.getCategory().name())
                    .templateParametersJson(paramsJson)
                    .status(WhatsAppMessage.Status.QUEUED)
                    .build();

            messageRepo.save(msg);
        }

        templateStatusSyncService.manuallyResetQueueCircuit();
        log.info("Queue circuit reset due to successful bulk scheduling");

        return ApiResponse.success("Campaign scheduled successfully", 
                Map.of("campaignId", campaign.getId(), 
                       "totalRecipients", req.getRecipients().size(),
                       "sender", account.getDisplayPhoneNumber()));
    }

    // ✅ NEW: Helper method for bulk requests
    private WhatsAppAccount getWhatsAppAccountForBulk(Long tenantId, BulkSendWhatsAppMessageRequest req) {
        if (req.getPhoneNumberId() != null && !req.getPhoneNumberId().isEmpty()) {
            // Use specified phone number
            return tokenService.validateAndGetAccountByPhoneNumberId(
                    tenantId, req.getWabaId(), req.getPhoneNumberId());
        } else {
            // Use default account
            return tokenService.validateAndGetActiveAccount(tenantId, req.getWabaId());
        }
    }

    public ApiResponse<?> getCampaignStatus(Long campaignId) {
        Long tenantId = currentTenantService.getTenantId();
        WhatsAppCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        if (!campaign.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Unauthorized access to campaign");
        }

        long total = messageRepo.countByCampaignId(campaignId);
        long queued = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.QUEUED);
        long pending = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.PENDING);
        long sent = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.SENT);
        long delivered = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.DELIVERED);
        long read = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.READ);
        long failed = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.FAILED);

        double progress = total == 0 ? 0 : ((double)(sent + delivered + read + failed) / total) * 100.0;

        CampaignStatusResponse response = CampaignStatusResponse.builder()
                .campaignId(campaignId)
                .status(campaign.getStatus().name())
                .total(total)
                .queued(queued)
                .pending(pending)
                .sent(sent)
                .delivered(delivered)
                .read(read)
                .failed(failed)
                .progressPercentage(Math.round(progress * 100.0) / 100.0)
                .build();

        return ApiResponse.success("Campaign status fetched", response);
    }
}