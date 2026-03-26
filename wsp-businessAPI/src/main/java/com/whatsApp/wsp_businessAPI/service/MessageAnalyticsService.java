package com.whatsApp.wsp_businessAPI.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage.Status;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageAnalyticsService {
    
    private final WhatsAppMessageRepository messageRepo;
    private final WhatsAppAccountRepository accountRepo;
    private final CurrentTenantService currentTenantService;
    
    // ===== EXISTING METHODS =====
    
    public Map<String, Object> getWabaSummary(String wabaId) {
        Long tenantId = currentTenantService.getTenantId();
        
        List<WhatsAppMessage> messages = messageRepo
            .findAllByTenantIdAndWabaId(tenantId, wabaId);
        
        return calculateSummary(messages);
    }
    
    public Map<String, Object> getPhoneNumberSummary(String wabaId, String phoneNumberId) {
        Long tenantId = currentTenantService.getTenantId();
        
        List<WhatsAppMessage> messages = messageRepo
            .findAllByTenantIdAndWabaIdAndPhoneNumberId(tenantId, wabaId, phoneNumberId);
        
        Map<String, Object> summary = calculateSummary(messages);
        summary.put("phoneNumberId", phoneNumberId);
        
        String displayNumber = accountRepo.findDisplayPhoneNumberByPhoneNumberId(phoneNumberId);
        summary.put("displayPhoneNumber", displayNumber != null ? displayNumber : "Unknown");
        
        return summary;
    }
    
    public List<Map<String, Object>> getAllPhoneNumbersSummary(String wabaId) {
        Long tenantId = currentTenantService.getTenantId();
        
        List<String> phoneNumberIds = messageRepo.findDistinctPhoneNumberIds(tenantId, wabaId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (String phoneNumberId : phoneNumberIds) {
            result.add(getPhoneNumberSummary(wabaId, phoneNumberId));
        }
        
        return result;
    }
    
    // ===== REPLY METHODS =====
    
    public Map<String, Object> getAllReplies() {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching all replies for tenant: {}", tenantId);
        
        List<WhatsAppMessage> replies = messageRepo
            .findByTenantIdAndCustomerRepliedTrueOrderByCustomerRepliedAtDesc(tenantId);
        
        return formatReplyResponse(replies, "All Replies");
    }
    
    public Map<String, Object> getCampaignReplies(Long campaignId) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching replies for campaign: {}, tenant: {}", campaignId, tenantId);
        
        List<WhatsAppMessage> replies = messageRepo.findByCampaignIdAndCustomerRepliedTrue(campaignId)
            .stream()
            .filter(msg -> msg.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
        
        if (replies.isEmpty()) {
            throw new ValidationException(
                "NO_REPLIES_FOUND",
                "No replies found for campaign ID: " + campaignId
            );
        }
        
        return formatReplyResponse(replies, "Campaign: " + campaignId);
    }
    
    public Map<String, Object> getPhoneNumberReplies(String phoneNumberId) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching replies for phone: {}, tenant: {}", phoneNumberId, tenantId);
        
        List<WhatsAppMessage> replies = messageRepo.findByPhoneNumberIdAndCustomerRepliedTrue(phoneNumberId)
            .stream()
            .filter(msg -> msg.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
        
        String displayNumber = accountRepo.findDisplayPhoneNumberByPhoneNumberId(phoneNumberId);
        
        Map<String, Object> response = formatReplyResponse(replies, "Phone: " + (displayNumber != null ? displayNumber : phoneNumberId));
        response.put("phoneNumberId", phoneNumberId);
        response.put("displayPhoneNumber", displayNumber != null ? displayNumber : "Unknown");
        
        return response;
    }
    
    public Map<String, Object> getTemplateReplies(String templateName, String wabaId) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching replies for template: {}, tenant: {}", templateName, tenantId);
        
        List<WhatsAppMessage> allMessages = messageRepo.findByTenantIdAndCustomerRepliedTrue(tenantId);
        
        List<WhatsAppMessage> replies = allMessages.stream()
            .filter(msg -> msg.getTemplateName().equalsIgnoreCase(templateName))
            .filter(msg -> wabaId == null || msg.getWabaId().equals(wabaId))
            .collect(Collectors.toList());
        
        Map<String, Object> response = formatReplyResponse(replies, "Template: " + templateName);
        response.put("templateName", templateName);
        if (wabaId != null) {
            response.put("wabaId", wabaId);
        }
        
        return response;
    }
    
    public Map<String, Object> searchReplies(String keyword) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Searching replies for keyword: {}, tenant: {}", keyword, tenantId);
        
        List<WhatsAppMessage> replies = messageRepo.searchCustomerReplies(tenantId, keyword);
        
        Map<String, Object> response = formatReplyResponse(replies, "Search: " + keyword);
        response.put("searchKeyword", keyword);
        
        return response;
    }
    
    public Map<String, Object> getRepliesByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching replies from {} to {} for tenant: {}", startDate, endDate, tenantId);
        
        List<WhatsAppMessage> replies = messageRepo
            .findByTenantIdAndCustomerRepliedTrueAndCustomerRepliedAtBetween(tenantId, startDate, endDate);
        
        Map<String, Object> response = formatReplyResponse(replies, "Date Range");
        response.put("startDate", startDate.toString());
        response.put("endDate", endDate.toString());
        
        return response;
    }
    
    public Map<String, Object> getReplyStats() {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching reply stats for tenant: {}", tenantId);
        
        long totalReplies = messageRepo.countByTenantIdAndCustomerRepliedTrue(tenantId);
        
        List<WhatsAppMessage> allReplies = messageRepo.findByTenantIdAndCustomerRepliedTrue(tenantId);
        
        Map<String, Long> repliesByType = allReplies.stream()
            .filter(msg -> msg.getCustomerReplyType() != null)
            .collect(Collectors.groupingBy(
                WhatsAppMessage::getCustomerReplyType,
                Collectors.counting()
            ));
        
        Map<String, Long> repliesByTemplate = allReplies.stream()
            .collect(Collectors.groupingBy(
                WhatsAppMessage::getTemplateName,
                Collectors.counting()
            ));
        
        List<Map<String, Object>> topTemplates = repliesByTemplate.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("templateName", entry.getKey());
                map.put("replyCount", entry.getValue());
                return map;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReplies", totalReplies);
        stats.put("repliesByType", repliesByType);
        stats.put("topTemplates", topTemplates);
        
        if (!allReplies.isEmpty()) {
            LocalDateTime earliest = allReplies.stream()
                .map(WhatsAppMessage::getCustomerRepliedAt)
                .filter(t -> t != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
            
            LocalDateTime latest = allReplies.stream()
                .map(WhatsAppMessage::getCustomerRepliedAt)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
            
            stats.put("earliestReply", earliest);
            stats.put("latestReply", latest);
        }
        
        return stats;
    }
    
    // ===== HELPER METHODS =====
    
    private Map<String, Object> formatReplyResponse(List<WhatsAppMessage> replies, String title) {
        List<Map<String, Object>> formattedReplies = replies.stream()
            .map(this::formatSingleReply)
            .collect(Collectors.toList());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", title);
        response.put("totalReplies", replies.size());
        response.put("replies", formattedReplies);
        
        return response;
    }
    
    private Map<String, Object> formatSingleReply(WhatsAppMessage msg) {
        Map<String, Object> reply = new LinkedHashMap<>();
        
        reply.put("messageId", msg.getId());
        reply.put("campaignId", msg.getCampaignId());
        reply.put("wabaId", msg.getWabaId());
        reply.put("phoneNumberId", msg.getPhoneNumberId());
        reply.put("customerPhone", msg.getPhoneNumber());
        reply.put("templateName", msg.getTemplateName());
        reply.put("templateLanguage", msg.getTemplateLanguage());
        
        String displayNumber = accountRepo.findDisplayPhoneNumberByPhoneNumberId(msg.getPhoneNumberId());
        reply.put("senderNumber", displayNumber != null ? displayNumber : "Unknown");
        
        reply.put("replyText", msg.getCustomerReplyText() != null ? msg.getCustomerReplyText() : "");
        reply.put("replyType", msg.getCustomerReplyType() != null ? msg.getCustomerReplyType() : "unknown");
        reply.put("repliedAt", msg.getCustomerRepliedAt());
        
        reply.put("originalStatus", msg.getStatus());
        reply.put("originalSentAt", msg.getCreatedAt());
        reply.put("originalDeliveredAt", msg.getDeliveredAt());
        reply.put("originalReadAt", msg.getReadAt());
        
        return reply;
    }
    
    /**
     * FIXED: Shared calculation logic with no double-counting and proper rates
     */
    private Map<String, Object> calculateSummary(List<WhatsAppMessage> messages) {
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
        
        // ===== SUCCESS CALCULATIONS =====
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
        
        // Verify total adds up
        long accountedTotal = successCount + inProgressCount + failed;
        if (accountedTotal != total) {
            log.warn("Analytics mismatch: total={}, accounted={}, sent={}, delivered={}, read={}, queued={}, pending={}, failed={}", 
                     total, accountedTotal, sent, delivered, read, queued, pending, failed);
        }
        
        // ===== BUILD MAIN RESPONSE WITH ORDERED MAP =====
        Map<String, Object> summary = new LinkedHashMap<>();
        
        // TOTAL VOLUME
        summary.put("totalSent", total);
        
        // POSITIVE / SUCCESS METRICS
        summary.put("sent", sent);
        summary.put("delivered", delivered);
        summary.put("read", read);
        summary.put("replied", replied);
        summary.put("successCount", successCount);
        
        // IN-PROGRESS METRICS
        summary.put("queued", queued);
        summary.put("pending", pending);
        summary.put("inProgressCount", inProgressCount);
        
        // FAILURE METRICS
        summary.put("failed", failed);
        
        // ===== RATES - ALL BASED ON TOTAL FOR CONSISTENCY =====
        Map<String, String> rates = new LinkedHashMap<>();
        rates.put("successRate", successPercentage);
        rates.put("failureRate", failurePercentage);
        rates.put("inProgressRate", inProgressPercentage);
        
        // Delivery rate: percentage of total that were delivered
        rates.put("deliveryRate", total > 0 ? (delivered * 100 / total) + "%" : "0%");
        
        // Read rate: percentage of total that were read
        rates.put("readRate", total > 0 ? (read * 100 / total) + "%" : "0%");
        
        // Response rate: percentage of total that replied
        rates.put("responseRate", total > 0 ? (replied * 100 / total) + "%" : "0%");
        
        summary.put("rates", rates);
        
        return summary;
    }
}