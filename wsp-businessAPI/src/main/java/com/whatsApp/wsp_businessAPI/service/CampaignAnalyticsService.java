package com.whatsApp.wsp_businessAPI.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppCampaign;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage.Status;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppCampaignRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignAnalyticsService {

    private final WhatsAppCampaignRepository campaignRepo;
    private final WhatsAppMessageRepository messageRepo;
    private final WhatsAppAccountRepository accountRepo;

    /**
     * Get campaign summary with message analytics
     */
    public Map<String, Object> getCampaignSummary(Long campaignId, Long tenantId) {
        
        log.info("Generating campaign summary for campaign: {}, tenant: {}", campaignId, tenantId);
        
        // Step 1: Verify campaign exists and belongs to tenant
        WhatsAppCampaign campaign = campaignRepo.findById(campaignId)
            .orElseThrow(() -> new ValidationException(
                "CAMPAIGN_NOT_FOUND",
                "Campaign not found with ID: " + campaignId
            ));
        
        if (!campaign.getTenantId().equals(tenantId)) {
            throw new ValidationException(
                "UNAUTHORIZED",
                "You don't have permission to access this campaign"
            );
        }
        
        // Step 2: Get all messages for this campaign
        List<WhatsAppMessage> messages = messageRepo.findAllByCampaignId(campaignId);
        
        if (messages.isEmpty()) {
            throw new ValidationException(
                "NO_MESSAGES_FOUND",
                "No messages found for campaign ID: " + campaignId
            );
        }
        
        // Step 3: Get sender phone number info from first message
        String phoneNumberId = messages.get(0).getPhoneNumberId();
        String displayPhoneNumber = "Unknown";
        String wabaId = campaign.getWabaId();
        
        Optional<WhatsAppAccount> accountOpt = accountRepo.findByPhoneNumberId(phoneNumberId);
        if (accountOpt.isPresent()) {
            displayPhoneNumber = accountOpt.get().getDisplayPhoneNumber();
        }
        
        // Step 4: Calculate analytics
        return calculateCampaignAnalytics(campaign, messages, phoneNumberId, displayPhoneNumber, wabaId);
    }
    
    /**
     * Get all campaigns for a specific template with aggregated analytics
     */
    public Map<String, Object> getCampaignsByTemplate(
            Long tenantId, 
            String templateName, 
            String wabaId) {
        
        log.info("Fetching campaigns for template: {}, tenant: {}", templateName, tenantId);
        
        // Build query to find campaigns by template name
        List<WhatsAppCampaign> campaigns;
        
        if (wabaId != null && !wabaId.isEmpty()) {
            campaigns = campaignRepo.findByTenantIdAndTemplateNameAndWabaId(
                tenantId, templateName, wabaId);
        } else {
            campaigns = campaignRepo.findByTenantIdAndTemplateName(
                tenantId, templateName);
        }
        
        if (campaigns.isEmpty()) {
            throw new ValidationException(
                "NO_CAMPAIGNS_FOUND",
                "No campaigns found for template: " + templateName
            );
        }
        
        // Get the template info from first campaign
        String templateLanguage = campaigns.get(0).getTemplateLanguage();
        
        // Prepare response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("templateName", templateName);
        response.put("templateLanguage", templateLanguage);
        response.put("totalCampaigns", campaigns.size());
        
        // Aggregated stats across all campaigns
        long totalRecipients = 0;
        long totalSent = 0;
        long totalDelivered = 0;
        long totalRead = 0;
        long totalReplied = 0;
        long totalFailed = 0;
        long totalSuccess = 0;
        
        List<Map<String, Object>> campaignsList = new ArrayList<>();
        
        for (WhatsAppCampaign campaign : campaigns) {
            List<WhatsAppMessage> messages = messageRepo.findAllByCampaignId(campaign.getId());
            
            // Calculate stats for this campaign
            Map<String, Object> campaignStats = calculateCampaignStats(campaign, messages);
            campaignsList.add(campaignStats);
            
            // Aggregate totals
            totalRecipients += campaign.getTotalContacts();
            totalSent += (long) campaignStats.get("sent");
            totalDelivered += (long) campaignStats.get("delivered");
            totalRead += (long) campaignStats.get("read");
            totalReplied += (long) campaignStats.get("replied");
            totalFailed += (long) campaignStats.get("failed");
            totalSuccess += (long) campaignStats.get("successCount");
        }
        
        // Add aggregated stats
        Map<String, Object> aggregatedStats = new LinkedHashMap<>();
        aggregatedStats.put("totalRecipients", totalRecipients);
        aggregatedStats.put("totalSent", totalSent);
        aggregatedStats.put("totalDelivered", totalDelivered);
        aggregatedStats.put("totalRead", totalRead);
        aggregatedStats.put("totalReplied", totalReplied);
        aggregatedStats.put("totalFailed", totalFailed);
        aggregatedStats.put("totalSuccess", totalSuccess);
        aggregatedStats.put("successRate", totalRecipients > 0 
            ? String.format("%.1f", (totalSuccess * 100.0 / totalRecipients)) + "%"
            : "0%");
        
        response.put("aggregatedStats", aggregatedStats);
        response.put("campaigns", campaignsList);
        
        return response;
    }

    /**
     * Get summary of all templates with campaign stats
     */
    public List<Map<String, Object>> getAllTemplatesSummary(Long tenantId, String wabaId) {
        
        // Find all distinct template names for this tenant
        List<String> templateNames;
        
        if (wabaId != null && !wabaId.isEmpty()) {
            templateNames = campaignRepo.findDistinctTemplateNamesByTenantIdAndWabaId(tenantId, wabaId);
        } else {
            templateNames = campaignRepo.findDistinctTemplateNamesByTenantId(tenantId);
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (String templateName : templateNames) {
            List<WhatsAppCampaign> campaigns;
            
            if (wabaId != null && !wabaId.isEmpty()) {
                campaigns = campaignRepo.findByTenantIdAndTemplateNameAndWabaId(
                    tenantId, templateName, wabaId);
            } else {
                campaigns = campaignRepo.findByTenantIdAndTemplateName(
                    tenantId, templateName);
            }
            
            if (campaigns.isEmpty()) continue;
            
            // Aggregate stats for this template
            long totalCampaigns = campaigns.size();
            long totalRecipients = 0;
            long totalSent = 0;
            long totalDelivered = 0;
            long totalRead = 0;
            long totalReplied = 0;
            long totalFailed = 0;
            long totalSuccess = 0;
            
            // Get WABA and sender info from first campaign's messages
            String wabaIdInfo = campaigns.get(0).getWabaId();
            String phoneNumberId = null;
            String senderNumber = "Unknown";
            
            List<WhatsAppMessage> firstCampaignMessages = messageRepo.findAllByCampaignId(campaigns.get(0).getId());
            if (!firstCampaignMessages.isEmpty()) {
                phoneNumberId = firstCampaignMessages.get(0).getPhoneNumberId();
                Optional<WhatsAppAccount> accountOpt = accountRepo.findByPhoneNumberId(phoneNumberId);
                if (accountOpt.isPresent()) {
                    senderNumber = accountOpt.get().getDisplayPhoneNumber();
                }
            }
            
            for (WhatsAppCampaign campaign : campaigns) {
                List<WhatsAppMessage> messages = messageRepo.findAllByCampaignId(campaign.getId());
                
                totalRecipients += campaign.getTotalContacts();
                
                long read = messages.stream()
                    .filter(m -> m.getReadAt() != null).count();
                
                long delivered = messages.stream()
                    .filter(m -> m.getDeliveredAt() != null && m.getReadAt() == null).count();
                
                long sent = messages.stream()
                    .filter(m -> m.getStatus() == Status.SENT && 
                                 m.getDeliveredAt() == null && 
                                 m.getReadAt() == null).count();
                
                long replied = messages.stream()
                    .filter(m -> m.getCustomerReplied() != null && m.getCustomerReplied()).count();
                
                long failed = messages.stream()
                    .filter(m -> m.getStatus() == Status.FAILED).count();
                
                totalSent += sent;
                totalDelivered += delivered;
                totalRead += read;
                totalReplied += replied;
                totalFailed += failed;
                totalSuccess += (sent + delivered + read);
            }
            
            Map<String, Object> templateSummary = new LinkedHashMap<>();
            templateSummary.put("templateName", templateName);
            templateSummary.put("wabaId", wabaIdInfo);
            templateSummary.put("senderNumber", senderNumber);
            templateSummary.put("totalCampaigns", totalCampaigns);
            templateSummary.put("totalRecipients", totalRecipients);
            templateSummary.put("totalSent", totalSent);
            templateSummary.put("totalDelivered", totalDelivered);
            templateSummary.put("totalRead", totalRead);
            templateSummary.put("totalReplied", totalReplied);
            templateSummary.put("totalFailed", totalFailed);
            templateSummary.put("totalSuccess", totalSuccess);
            templateSummary.put("successRate", totalRecipients > 0 
                ? String.format("%.1f", (totalSuccess * 100.0 / totalRecipients)) + "%"
                : "0%");
            
            result.add(templateSummary);
        }
        
        return result;
    }

    /**
     * Calculate campaign analytics in organized format
     */
    private Map<String, Object> calculateCampaignAnalytics(
            WhatsAppCampaign campaign,
            List<WhatsAppMessage> messages,
            String phoneNumberId,
            String displayPhoneNumber,
            String wabaId) {
        
        long total = messages.size();
        
        // ===== COUNT AT HIGHEST STAGE (no double-counting) =====
        long read = messages.stream()
            .filter(m -> m.getReadAt() != null).count();
        
        long delivered = messages.stream()
            .filter(m -> m.getDeliveredAt() != null && m.getReadAt() == null).count();
        
        long sent = messages.stream()
            .filter(m -> m.getStatus() == Status.SENT && 
                         m.getDeliveredAt() == null && 
                         m.getReadAt() == null).count();
        
        long replied = messages.stream()
            .filter(m -> m.getCustomerReplied() != null && m.getCustomerReplied()).count();
        
        // ===== PENDING / IN-PROGRESS METRICS =====
        long queued = messages.stream()
            .filter(m -> m.getStatus() == Status.QUEUED).count();
        long pending = messages.stream()
            .filter(m -> m.getStatus() == Status.PENDING).count();
        
        // ===== FAILURE METRICS =====
        long failed = messages.stream()
            .filter(m -> m.getStatus() == Status.FAILED).count();
        
        // ===== SUCCESS CALCULATIONS (NOW CORRECT) =====
        long successCount = sent + delivered + read;
        String successPercentage = total > 0 
            ? String.format("%.1f", (successCount * 100.0 / total)) + "%"
            : "0%";
        
        // ===== FAILURE CALCULATIONS =====
        String failurePercentage = total > 0 
            ? String.format("%.1f", (failed * 100.0 / total)) + "%"
            : "0%";
        
        // ===== IN-PROGRESS CALCULATIONS =====
        long inProgressCount = queued + pending;
        String inProgressPercentage = total > 0 
            ? String.format("%.1f", (inProgressCount * 100.0 / total)) + "%"
            : "0%";
        
        // Verify total adds up (should be 100%)
        long accountedTotal = successCount + inProgressCount + failed;
        if (accountedTotal != total) {
            log.warn("Analytics mismatch for campaign {}: total={}, accounted={}", 
                     campaign.getId(), total, accountedTotal);
        }
        
        // ===== BUILD RESPONSE WITH ORDERED MAP =====
        Map<String, Object> summary = new LinkedHashMap<>();
        
        // 1. Campaign Identifiers
        summary.put("campaignId", campaign.getId());
        summary.put("wabaId", wabaId);
        summary.put("phoneNumberId", phoneNumberId);
        summary.put("senderNumber", displayPhoneNumber);
        
        // 2. Campaign Info
        summary.put("templateName", campaign.getTemplateName());
        summary.put("templateLanguage", campaign.getTemplateLanguage());
        summary.put("campaignStatus", campaign.getStatus());
        summary.put("createdAt", campaign.getCreatedAt());
        
        // 3. Total Volume
        summary.put("totalRecipients", campaign.getTotalContacts());
        summary.put("totalMessages", total);
        
        // 4. POSITIVE / SUCCESS METRICS (ordered - highest stage to lowest)
        summary.put("read", read);
        summary.put("delivered", delivered);
        summary.put("sent", sent);
        summary.put("replied", replied);
        summary.put("successCount", successCount);
        
        // 5. IN-PROGRESS METRICS
        summary.put("queued", queued);
        summary.put("pending", pending);
        summary.put("inProgressCount", inProgressCount);
        
        // 6. FAILURE METRICS
        summary.put("failed", failed);
        
        // 7. RATES (organized in ordered map)
        Map<String, String> rates = new LinkedHashMap<>();
        rates.put("successRate", successPercentage);
        rates.put("failureRate", failurePercentage);
        rates.put("inProgressRate", inProgressPercentage);
        rates.put("deliveryRate", total > 0 ? (delivered * 100 / total) + "%" : "0%");
        rates.put("readRate", total > 0 ? (read * 100 / total) + "%" : "0%");
        rates.put("responseRate", total > 0 ? (replied * 100 / total) + "%" : "0%");
        
        summary.put("rates", rates);
        
        // 8. Progress Info
        summary.put("processedContacts", campaign.getProcessedContacts());
        summary.put("progressPercentage", 
            campaign.getTotalContacts() > 0 
                ? String.format("%.1f", (campaign.getProcessedContacts() * 100.0 / campaign.getTotalContacts())) + "%"
                : "0%");
        
        return summary;
    }
    
    /**
     * Helper method to calculate stats for a single campaign
     */
    private Map<String, Object> calculateCampaignStats(WhatsAppCampaign campaign, List<WhatsAppMessage> messages) {
        long total = messages.size();
        
        long read = messages.stream()
            .filter(m -> m.getReadAt() != null).count();
        
        long delivered = messages.stream()
            .filter(m -> m.getDeliveredAt() != null && m.getReadAt() == null).count();
        
        long sent = messages.stream()
            .filter(m -> m.getStatus() == Status.SENT && 
                         m.getDeliveredAt() == null && 
                         m.getReadAt() == null).count();
        
        long replied = messages.stream()
            .filter(m -> m.getCustomerReplied() != null && m.getCustomerReplied()).count();
        
        long queued = messages.stream()
            .filter(m -> m.getStatus() == Status.QUEUED).count();
        
        long pending = messages.stream()
            .filter(m -> m.getStatus() == Status.PENDING).count();
        
        long failed = messages.stream()
            .filter(m -> m.getStatus() == Status.FAILED).count();
        
        long successCount = sent + delivered + read;
        
        // Get sender info from first message
        String phoneNumberId = messages.isEmpty() ? "Unknown" : messages.get(0).getPhoneNumberId();
        String senderNumber = "Unknown";
        
        if (!messages.isEmpty()) {
            Optional<WhatsAppAccount> accountOpt = accountRepo.findByPhoneNumberId(phoneNumberId);
            if (accountOpt.isPresent()) {
                senderNumber = accountOpt.get().getDisplayPhoneNumber();
            }
        }
        
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("campaignId", campaign.getId());
        stats.put("phoneNumberId", phoneNumberId);
        stats.put("senderNumber", senderNumber);
        stats.put("createdAt", campaign.getCreatedAt());
        stats.put("status", campaign.getStatus());
        stats.put("totalRecipients", campaign.getTotalContacts());
        stats.put("sent", sent);
        stats.put("delivered", delivered);
        stats.put("read", read);
        stats.put("replied", replied);
        stats.put("queued", queued); 
        stats.put("pending", pending);
        stats.put("failed", failed);
        stats.put("successCount", successCount);
        stats.put("successRate", total > 0 
            ? String.format("%.1f", (successCount * 100.0 / total)) + "%"
            : "0%");
        
        return stats;
    }
}