package com.whatsApp.wsp_businessAPI.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.service.BulkUploadAnalyticsService;
import com.whatsApp.wsp_businessAPI.service.CampaignAnalyticsService;
import com.whatsApp.wsp_businessAPI.service.CurrentTenantService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/whatsapp/analytics")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppAnalyticsController {

    private final BulkUploadAnalyticsService analyticsService;
    private final CampaignAnalyticsService campaignAnalyticsService;
    private final CurrentTenantService currentTenantService;

    // EXISTING - Keep as is (download feature)
    @GetMapping("/campaign/{campaignId}/download")
    public ResponseEntity<ApiResponse<?>> downloadCampaignAnalytics(
            @PathVariable Long campaignId) {
        
        Long tenantId = currentTenantService.getTenantId();
        log.info("Download analytics request for campaign: {}, tenant: {}", campaignId, tenantId);
        
        try {
            String fileUrl = analyticsService.generateCampaignAnalytics(campaignId, tenantId);
            
            return ResponseEntity.ok(
                ApiResponse.success(
                    "Analytics file generated successfully",
                    Map.of(
                        "campaignId", campaignId,
                        "fileUrl", fileUrl,
                        "message", "Use the URL to download the analytics file"
                    )
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to generate analytics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, 
                    "Failed to generate analytics", 
                    "ANALYTICS_ERROR",
                    e.getMessage()));
        }
    }
    
    // NEW: Campaign Summary (numbers analytics)
    @GetMapping("/campaign/{campaignId}/summary")
    public ResponseEntity<ApiResponse<?>> getCampaignSummary(
            @PathVariable Long campaignId) {
        
        Long tenantId = currentTenantService.getTenantId();
        log.info("Campaign summary request for campaign: {}, tenant: {}", campaignId, tenantId);
        
        try {
            Map<String, Object> summary = campaignAnalyticsService.getCampaignSummary(campaignId, tenantId);
            
            return ResponseEntity.ok(
                ApiResponse.success(
                    "Campaign analytics summary",
                    summary
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to get campaign summary: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, 
                    "Failed to get campaign summary", 
                    "CAMPAIGN_ANALYTICS_ERROR",
                    e.getMessage()));
        }
    }
    
 // NEW: Get campaign analytics by template name
    @GetMapping("/template/{templateName}/campaigns")
    public ResponseEntity<ApiResponse<?>> getCampaignsByTemplate(
            @PathVariable String templateName,
            @RequestParam(required = false) String wabaId) {  // Removed phoneNumberId
        
        Long tenantId = currentTenantService.getTenantId();
        log.info("Campaign summary by template: {}, tenant: {}", templateName, tenantId);
        
        try {
            Map<String, Object> result = campaignAnalyticsService.getCampaignsByTemplate(
                tenantId, templateName, wabaId);  // Removed phoneNumberId
            
            return ResponseEntity.ok(
                ApiResponse.success(
                    "Campaign analytics by template",
                    result
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to get campaigns by template: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, 
                    "Failed to get campaigns", 
                    "TEMPLATE_ANALYTICS_ERROR",
                    e.getMessage()));
        }
    }

    // NEW: Get summary of all templates with campaign stats
    @GetMapping("/templates/summary")
    public ResponseEntity<ApiResponse<?>> getAllTemplatesSummary(
            @RequestParam(required = false) String wabaId) {
        
        Long tenantId = currentTenantService.getTenantId();
        log.info("All templates summary for tenant: {}", tenantId);
        
        try {
            List<Map<String, Object>> result = campaignAnalyticsService.getAllTemplatesSummary(tenantId, wabaId);
            
            return ResponseEntity.ok(
                ApiResponse.success(
                    "All templates summary",
                    result
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to get templates summary: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, 
                    "Failed to get templates summary", 
                    "TEMPLATES_SUMMARY_ERROR",
                    e.getMessage()));
        }
    }
    
}