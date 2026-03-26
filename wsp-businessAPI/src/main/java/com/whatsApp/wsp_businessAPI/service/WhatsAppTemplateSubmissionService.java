package com.whatsApp.wsp_businessAPI.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateSubmissionService {

    private final WhatsAppTemplateRepository templateRepo;
    private final WhatsAppAccountRepository accountRepo;
    private final MetaApiClient metaApiClient;
    private final CurrentTenantService currentTenantService;

    @Transactional
    public ApiResponse<?> submitTemplate(Long templateId) {

        Long tenantId = currentTenantService.getTenantId();

        WhatsAppTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new ValidationException("Template not found", "Invalid template ID"));

        if (!template.getTenantId().equals(tenantId)) {
            throw new ValidationException("Forbidden", "You do not have permission to submit this template");
        }

        if (template.getStatus() != WhatsAppTemplate.Status.DRAFT) {
            throw new ValidationException(
                    "Only DRAFT templates can be submitted",
                    "Current status: " + template.getStatus()
            );
        }

        WhatsAppAccount account = accountRepo.findByTenantIdAndWabaId(tenantId, template.getWabaId())
                .orElseThrow(() -> new ValidationException(
                        "WhatsApp account not found",
                        "WhatsApp not connected for this tenant"
                ));

        Map<String, Object> metaResp = metaApiClient.submitTemplate(
                template.getWabaId(),
                account.getSystemUserToken(),
                template.getName(),
                template.getCategory().name(),
                template.getLanguage(),
                template.getComponentsJson()
        );

        if (metaResp == null || !metaResp.containsKey("id")) {
            throw new ValidationException(
                    "Meta rejected template",
                    "Template creation failed in WhatsApp Manager"
            );
        }

        String metaTemplateId = metaResp.get("id").toString();
        template.setMetaTemplateId(metaTemplateId);
        template.setStatus(WhatsAppTemplate.Status.SUBMITTED);

        templateRepo.save(template);

        log.info("Template submitted. tenant={}, templateId={}, metaId={}",
                tenantId, template.getId(), metaTemplateId);

        return ApiResponse.success(
                "Template submitted to Meta for review",
                Map.of(
                        "templateId", template.getId(),
                        "metaTemplateId", metaTemplateId,
                        "status", template.getStatus()
                )
        );
    }
}