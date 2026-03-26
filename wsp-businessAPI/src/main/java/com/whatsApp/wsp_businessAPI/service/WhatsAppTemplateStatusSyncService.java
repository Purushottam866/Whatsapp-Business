package com.whatsApp.wsp_businessAPI.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.google.common.util.concurrent.RateLimiter;
import com.whatsApp.wsp_businessAPI.entity.User;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppCampaign;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.repository.UserRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppCampaignRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppTemplateRepository;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateStatusSyncService {

    private final WhatsAppTemplateRepository templateRepo;
    private final WhatsAppAccountRepository accountRepo;
    private final UserRepository userRepo;
    private final MetaApiClient metaApiClient;
    
    private final WhatsAppMessageRepository messageRepo;
    private final WhatsAppCampaignRepository campaignRepo;
    private final ObjectMapper objectMapper;

    // ==================== BATCH PROCESSING CONFIGURATION ====================
    
    @Value("${whatsapp.batch.size:4}")
    private int batchSize;  // Number of messages to process per second
    
    @Value("${whatsapp.batch.delay-ms:50}")
    private int delayBetweenMessagesMs;  // Delay between messages in same batch
    
    @Value("${whatsapp.max-messages-per-minute:200}")
    private int maxMessagesPerMinute;  // Safety cap
    
    // Rate limiter for precise control
    private RateLimiter rateLimiter;
    
    @PostConstruct
    public void init() {
        // Initialize rate limiter with messages per second
        double permitsPerSecond = maxMessagesPerMinute / 60.0;
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
        log.info("🚀 Rate limiter initialized: {} messages/second ({} per minute)", 
                 permitsPerSecond, maxMessagesPerMinute);
    }

    // ==================== TEMPLATE STATUS SYNC ====================
    
    @Scheduled(fixedDelay = 1200000) // 20 minutes
    @Transactional
    public void syncTemplateStatuses() {
        log.info("========== 🔄 TEMPLATE STATUS SYNC START ==========");
        
        List<WhatsAppTemplate> templates = templateRepo.findByStatusIn(
                List.of(
                    WhatsAppTemplate.Status.SUBMITTED, 
                    WhatsAppTemplate.Status.PENDING,
                    WhatsAppTemplate.Status.APPROVED, 
                    WhatsAppTemplate.Status.REJECTED
                ));

        log.info("Found {} templates to sync", templates.size());
        
        if (templates.isEmpty()) {
            log.info("No templates to sync");
            return;
        }

        for (WhatsAppTemplate template : templates) {
            log.info("Syncing template ID: {}, Name: {}, Current Status: {}", 
                     template.getId(), template.getName(), template.getStatus());
            
            try {
                // Get ALL WhatsApp accounts for this tenant and WABA
                List<WhatsAppAccount> accounts = accountRepo.findAllByTenantIdAndWabaId(
                        template.getTenantId(), template.getWabaId());
                
                if (accounts.isEmpty()) {
                    log.warn("Skipping template {} — no accounts found for tenant: {}", 
                            template.getName(), template.getTenantId());
                    continue;
                }

                // Use the first account's token (all accounts share the same token)
                WhatsAppAccount account = accounts.get(0);
                
                if (account.getSystemUserToken() == null) {
                    log.warn("Skipping template {} — missing account token", template.getName());
                    continue;
                }

                String userIdentifier = getUserIdentifier(template.getTenantId(), template.getWabaId());
                log.info("Fetching from Meta for template: {} ({})", template.getName(), userIdentifier);
                
                JsonNode resp = metaApiClient.fetchTemplates(template.getWabaId(), account.getSystemUserToken());
                JsonNode data = resp.get("data");

                log.info("Meta returned {} templates", data != null ? data.size() : 0);

                if (data == null || !data.isArray()) {
                    log.warn("No data array in Meta response");
                    continue;
                }

                boolean found = false;
                for (JsonNode node : data) {
                    String name = node.has("name") ? node.get("name").asText() : null;
                    if (name == null) continue;

                    JsonNode langNode = node.get("language");
                    String language;
                    if (langNode == null) continue;
                    if (langNode.isTextual()) {
                        language = langNode.asText();
                    } else if (langNode.has("code")) {
                        language = langNode.get("code").asText();
                    } else continue;

                    if (!name.equalsIgnoreCase(template.getName()) || 
                        !language.equalsIgnoreCase(template.getLanguage())) {
                        continue;
                    }

                    found = true;
                    String metaStatus = node.has("status") ? node.get("status").asText() : null;
                    log.info("Meta status for {}: {}", name, metaStatus);

                    WhatsAppTemplate.Status newStatus = mapMetaStatus(metaStatus);
                    
                    if (template.getStatus() != newStatus) {
                        template.setStatus(newStatus);
                        template.setLastStatusCheckAt(LocalDateTime.now());
                        templateRepo.save(template);
                        log.info("✅ Template {} status updated: {} -> {}", 
                                 template.getName(), template.getStatus(), newStatus);
                    } else {
                        log.info("No status change for {}", template.getName());
                        template.setLastStatusCheckAt(LocalDateTime.now());
                        templateRepo.save(template);
                    }
                    break;
                }
                
                if (!found) {
                    log.warn("Template {} not found in Meta response", template.getName());
                }
                
            } catch (Exception e) {
                log.error("Template sync failed for {}", template.getName(), e);
            }
        }
        log.info("========== 🔄 TEMPLATE STATUS SYNC COMPLETE ==========");
    }
    
    private String getUserIdentifier(Long tenantId, String wabaId) {
        if (tenantId == null) return "Unknown User (WABA: " + wabaId + ")";
        Optional<User> userOpt = userRepo.findByTenantId(tenantId);
        return userOpt.map(user -> String.format("%s (WABA: %s)", user.getFullName(), wabaId))
                .orElse("Tenant: " + tenantId + " (WABA: " + wabaId + ")");
    }
    
    private WhatsAppTemplate.Status mapMetaStatus(String metaStatus) {
        if (metaStatus == null) return WhatsAppTemplate.Status.PENDING;
        switch (metaStatus.toUpperCase()) {
            case "APPROVED": return WhatsAppTemplate.Status.APPROVED;
            case "PENDING": 
            case "PENDING_DELETION": 
            case "PENDING_REVIEW": 
            case "PENDING_FLAGGED": 
                return WhatsAppTemplate.Status.PENDING;
            case "REJECTED": return WhatsAppTemplate.Status.REJECTED;
            case "PAUSED": 
            case "DISABLED": 
                return WhatsAppTemplate.Status.DISABLED;
            case "IN_APPEAL": return WhatsAppTemplate.Status.IN_APPEAL;
            case "FLAGGED": return WhatsAppTemplate.Status.FLAGGED;
            default: 
                log.warn("Unknown Meta status: {}, defaulting to PENDING", metaStatus); 
                return WhatsAppTemplate.Status.PENDING;
        }
    }

    // ==================== ENHANCED MESSAGE QUEUE PROCESSOR WITH BATCHING ====================
    
    private int queueConsecutiveFailures = 0;
    private LocalDateTime queueCircuitOpenUntil = null;
    private static final int QUEUE_MAX_CONSECUTIVE_FAILURES = 5;
    private static final int QUEUE_CIRCUIT_OPEN_MINUTES = 5;

    @Scheduled(fixedDelay = 1000) // Runs every second
    @Transactional
    public void processMessageQueue() {
        
        try {
            // Queue circuit breaker check
            if (isQueueCircuitOpen()) {
                log.warn("📌 Queue circuit is OPEN - skipping message processing");
                return;
            }
            
            // Get batch of queued messages (multiple at once)
            List<WhatsAppMessage> messages = messageRepo.findTopNByStatusOrderByCreatedAtAsc(
                    WhatsAppMessage.Status.QUEUED, batchSize);
                    
            if (messages.isEmpty()) {
                // No messages in queue, reset circuit if it was previously failing
                if (queueConsecutiveFailures > 0) {
                    resetQueueCircuit();
                    log.info("📌 Queue circuit reset - no messages in queue");
                }
                return;
            }

            log.info("📌 Processing batch of {} messages (batch size: {}, rate limit: {}/min)", 
                     messages.size(), batchSize, maxMessagesPerMinute);
            
            int successCount = 0;
            int failureCount = 0;
            
            for (WhatsAppMessage msg : messages) {
                try {
                    // Apply rate limiting - this will block if we're sending too fast
                    rateLimiter.acquire();
                    
                    // Process individual message
                    boolean success = processSingleMessage(msg);
                    
                    if (success) {
                        successCount++;
                        // Reset circuit on any success
                        resetQueueCircuit();
                    } else {
                        failureCount++;
                    }
                    
                    // Small delay between messages in same batch to avoid throttling
                    if (delayBetweenMessagesMs > 0 && messages.size() > 1) {
                        Thread.sleep(delayBetweenMessagesMs);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("📌 Batch processing interrupted", e);
                    break;
                } catch (Exception e) {
                    log.error("📌 Error processing message {} in batch", msg.getId(), e);
                    failureCount++;
                    incrementQueueFailureCount();
                }
            }
            
            log.info("📌 Batch complete: {} successful, {} failed", successCount, failureCount);
            
        } catch (Exception e) {
            log.error("📌 Queue processor error", e);
            incrementQueueFailureCount();
        }
    }
    
    /**
     * Process a single message with proper error handling
     */
    private boolean processSingleMessage(WhatsAppMessage msg) {
        log.info("📌 Processing message ID: {}, To: {}, From phone ID: {}", 
                 msg.getId(), msg.getPhoneNumber(), msg.getPhoneNumberId());
        
        // Skip messages that have failed too many times
        if (msg.getFailureCount() != null && msg.getFailureCount() >= 3) {
            log.warn("📌 Message {} failed 3 times, marking as permanent failure", msg.getId());
            msg.setStatus(WhatsAppMessage.Status.FAILED);
            msg.setFailureReason("Permanent failure after 3 retry attempts");
            messageRepo.save(msg);
            return false;
        }

        // Find WhatsApp accounts for this tenant and WABA
        List<WhatsAppAccount> accounts = accountRepo.findAllByTenantIdAndWabaId(
                msg.getTenantId(), msg.getWabaId());
                
        if (accounts.isEmpty()) {
            log.warn("📌 Message {}: No WhatsApp accounts found for tenant {} and WABA {}", 
                     msg.getId(), msg.getTenantId(), msg.getWabaId());
            msg.setStatus(WhatsAppMessage.Status.FAILED);
            msg.setFailureReason("Missing WhatsApp account/token");
            messageRepo.save(msg);
            incrementQueueFailureCount();
            return false;
        }

        // Choose which account to use for sending
        WhatsAppAccount account = null;
        
        // If message has a specific phoneNumberId stored, try to use that
        if (msg.getPhoneNumberId() != null && !msg.getPhoneNumberId().isEmpty()) {
            Optional<WhatsAppAccount> matchingAccount = accounts.stream()
                    .filter(a -> a.getPhoneNumberId().equals(msg.getPhoneNumberId()))
                    .findFirst();
            
            if (matchingAccount.isPresent()) {
                account = matchingAccount.get();
                log.info("📌 Using specified phone number ID: {} ({})", 
                         account.getPhoneNumberId(), account.getDisplayPhoneNumber());
            } else {
                // Fall back to first account if specified not found
                account = accounts.get(0);
                log.warn("📌 Specified phone number ID {} not found, using first account: {} ({})", 
                         msg.getPhoneNumberId(), account.getPhoneNumberId(), 
                         account.getDisplayPhoneNumber());
            }
        } else {
            // Use first account as default
            account = accounts.get(0);
            log.info("📌 Using default phone number ID: {} ({})", 
                     account.getPhoneNumberId(), account.getDisplayPhoneNumber());
        }

        // Validate account has token
        if (account.getSystemUserToken() == null || account.getSystemUserToken().isBlank()) {
            log.warn("📌 Message {}: WhatsApp account has no token", msg.getId());
            msg.setStatus(WhatsAppMessage.Status.FAILED);
            msg.setFailureReason("WhatsApp account has no token");
            messageRepo.save(msg);
            incrementQueueFailureCount();
            return false;
        }

        // Parse stored parameters
        Map<String, Object> parameters = null;
        if (msg.getTemplateParametersJson() != null) {
            try {
                parameters = objectMapper.readValue(msg.getTemplateParametersJson(), Map.class);
                log.debug("📌 Parsed parameters: {}", parameters);
            } catch (Exception e) {
                log.warn("📌 Failed to parse stored parameters for message {}", msg.getId());
            }
        }

        try {
            log.info("📌 Sending message {} to Meta using phone ID: {}", 
                     msg.getId(), account.getPhoneNumberId());
            Map<String, Object> metaResp = metaApiClient.sendTemplateMessage(
                    account.getPhoneNumberId(), 
                    account.getSystemUserToken(), 
                    msg.getPhoneNumber(),
                    msg.getTemplateName(), 
                    msg.getTemplateLanguage(), 
                    parameters
            );

            if (metaResp != null && metaResp.containsKey("messages")) {
                List<Map<String, Object>> messages = (List<Map<String, Object>>) metaResp.get("messages");
                if (messages != null && !messages.isEmpty()) {
                    msg.setMetaMessageId(messages.get(0).get("id").toString());
                    msg.setStatus(WhatsAppMessage.Status.SENT);
                    msg.setFailureCount(0);
                    msg.setFailureReason(null);
                    messageRepo.save(msg);
                    
                    log.info("📌✅ Message {} sent successfully with ID: {} from phone: {}", 
                             msg.getId(), msg.getMetaMessageId(), account.getDisplayPhoneNumber());
                    
                    // Update campaign progress
                    updateCampaignProgress(msg);
                    
                    return true;
                } else {
                    log.warn("📌 Message {}: Meta returned empty messages array", msg.getId());
                    msg.setStatus(WhatsAppMessage.Status.FAILED);
                    msg.setFailureReason("Meta returned empty messages array");
                    messageRepo.save(msg);
                    incrementQueueFailureCount();
                    return false;
                }
            } else {
                log.warn("📌 Message {}: Meta rejected message - no response", msg.getId());
                msg.setStatus(WhatsAppMessage.Status.FAILED);
                msg.setFailureReason("Meta rejected message - no response");
                messageRepo.save(msg);
                incrementQueueFailureCount();
                return false;
            }

        } catch (HttpClientErrorException.BadRequest e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("📌 Bad request for message {}: {}", msg.getId(), responseBody);
            
            if (responseBody.contains("wamid")) {
                try {
                    Pattern pattern = Pattern.compile("wamid\\.([^\"]+)");
                    Matcher matcher = pattern.matcher(responseBody);
                    if (matcher.find()) {
                        msg.setMetaMessageId("wamid." + matcher.group(1));
                        msg.setStatus(WhatsAppMessage.Status.SENT);
                        msg.setFailureReason("Message sent but Meta returned 400");
                        messageRepo.save(msg);
                        log.info("📌✅ Message {} actually sent despite 400 error", msg.getId());
                        updateCampaignProgress(msg);
                        return true;
                    } else {
                        msg.setStatus(WhatsAppMessage.Status.FAILED);
                        msg.setFailureReason("Template parameter mismatch");
                        messageRepo.save(msg);
                        incrementQueueFailureCount();
                        return false;
                    }
                } catch (Exception ex) {
                    log.warn("📌 Could not extract message ID from error");
                    msg.setStatus(WhatsAppMessage.Status.FAILED);
                    messageRepo.save(msg);
                    incrementQueueFailureCount();
                    return false;
                }
            } else {
                msg.setStatus(WhatsAppMessage.Status.FAILED);
                msg.setFailureReason("Template parameter mismatch");
                messageRepo.save(msg);
                incrementQueueFailureCount();
                return false;
            }
            
        } catch (HttpClientErrorException e) {
            log.error("📌 HTTP error for message {}: {}", msg.getId(), e.getResponseBodyAsString());
            incrementQueueFailureCount();
            msg.setFailureCount(msg.getFailureCount() == null ? 1 : msg.getFailureCount() + 1);
            msg.setLastAttemptAt(LocalDateTime.now());
            messageRepo.save(msg);
            return false;
            
        } catch (Exception e) {
            log.error("📌 Unexpected error for message {}: {}", msg.getId(), e.getMessage());
            incrementQueueFailureCount();
            msg.setFailureCount(msg.getFailureCount() == null ? 1 : msg.getFailureCount() + 1);
            msg.setLastAttemptAt(LocalDateTime.now());
            messageRepo.save(msg);
            return false;
        }
    }
    
    /**
     * Update campaign progress when message is sent
     */
    private void updateCampaignProgress(WhatsAppMessage msg) {
        if (msg.getCampaignId() != null) {
            try {
                WhatsAppCampaign campaign = campaignRepo.findById(msg.getCampaignId()).orElse(null);
                if (campaign != null) {
                    campaign.setProcessedContacts(campaign.getProcessedContacts() + 1);
                    campaign.setStatus(campaign.getProcessedContacts() >= campaign.getTotalContacts() 
                            ? WhatsAppCampaign.Status.COMPLETED : WhatsAppCampaign.Status.RUNNING);
                    campaignRepo.save(campaign);
                    log.info("📌 Campaign {} progress: {}/{}", campaign.getId(), 
                             campaign.getProcessedContacts(), campaign.getTotalContacts());
                }
            } catch (Exception e) {
                log.error("📌 Failed to update campaign progress for message {}", msg.getId(), e);
            }
        }
    }

    // Queue circuit breaker methods
    private boolean isQueueCircuitOpen() {
        if (queueCircuitOpenUntil != null && LocalDateTime.now().isBefore(queueCircuitOpenUntil)) {
            log.debug("📌 Queue circuit is open until {}", queueCircuitOpenUntil);
            return true;
        }
        if (queueCircuitOpenUntil != null && LocalDateTime.now().isAfter(queueCircuitOpenUntil)) {
            log.info("📌 Queue circuit automatically closed");
            queueCircuitOpenUntil = null;
            queueConsecutiveFailures = 0;
        }
        return false;
    }

    private void incrementQueueFailureCount() {
        queueConsecutiveFailures++;
        log.debug("📌 Queue failure count: {}/{}", queueConsecutiveFailures, QUEUE_MAX_CONSECUTIVE_FAILURES);
        if (queueConsecutiveFailures >= QUEUE_MAX_CONSECUTIVE_FAILURES) {
            queueCircuitOpenUntil = LocalDateTime.now().plusMinutes(QUEUE_CIRCUIT_OPEN_MINUTES);
            log.warn("📌🔴 Queue circuit OPENED for {} minutes due to {} consecutive failures", 
                     QUEUE_CIRCUIT_OPEN_MINUTES, queueConsecutiveFailures);
        }
    }

    private void resetQueueCircuit() {
        if (queueConsecutiveFailures > 0 || queueCircuitOpenUntil != null) {
            log.info("📌🟢 Queue circuit reset. Previous failures: {}", queueConsecutiveFailures);
            queueConsecutiveFailures = 0;
            queueCircuitOpenUntil = null;
        }
    }
    
    // Public method to manually reset queue circuit
    public void manuallyResetQueueCircuit() {
        resetQueueCircuit();
        log.info("📌 Queue circuit manually reset via API");
    }
}