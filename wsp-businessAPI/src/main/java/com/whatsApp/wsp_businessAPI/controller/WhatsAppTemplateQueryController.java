package com.whatsApp.wsp_businessAPI.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;
import com.whatsApp.wsp_businessAPI.service.CurrentTenantService;
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

    @GetMapping
    public ResponseEntity<ApiResponse<?>> getTemplates() {

        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching templates for tenant: {}", tenantId);

        // Get the WhatsApp account for this tenant
        WhatsAppAccount account = accountRepo.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("No WhatsApp account connected for this tenant"));

        String wabaId = account.getWabaId();
        log.info("Found WABA ID: {} for tenant: {}", wabaId, tenantId);

        List<WhatsAppTemplate> templates = templateRepo.findByTenantIdAndWabaId(tenantId, wabaId);

        if (templates == null || templates.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.notFound("No templates found for this WhatsApp account"));
        }

        log.info("Returning {} templates for tenant: {}", templates.size(), tenantId);
        
        return ResponseEntity.ok(
                ApiResponse.success("Templates fetched successfully", templates)
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