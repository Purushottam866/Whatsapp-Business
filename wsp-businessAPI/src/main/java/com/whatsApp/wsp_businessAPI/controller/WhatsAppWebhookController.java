package com.whatsApp.wsp_businessAPI.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppCampaign;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppCampaignRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;
import com.whatsApp.wsp_businessAPI.service.WhatsAppTemplateWebhookHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/webhook")
@Slf4j
@RequiredArgsConstructor
public class WhatsAppWebhookController {
	
	private final WhatsAppTemplateWebhookHandler templateWebhookHandler;
	private final WhatsAppMessageRepository messageRepo;
	private final WhatsAppCampaignRepository campaignRepo;

    private static final String VERIFY_TOKEN = "whatsapp_verify_123";

    @GetMapping
    public ResponseEntity<String> verifyWebhook(HttpServletRequest request) {

        String mode = request.getParameter("hub.mode");
        String challenge = request.getParameter("hub.challenge");
        String token = request.getParameter("hub.verify_token");

        log.info("Webhook verification request received");
        log.info("mode={}, token={}, challenge={}", mode, token, challenge);

        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            log.info("WEBHOOK VERIFIED SUCCESSFULLY");
            return ResponseEntity.ok(challenge);
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(@RequestBody JsonNode payload, 
                                                 HttpServletRequest request) {

        log.info("=========== WEBHOOK HIT ===========");
        log.info("Remote IP: {}", request.getRemoteAddr());
        
        Map<String, String> headersMap = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headersMap.put(headerName, request.getHeader(headerName));
        }
        log.info("Headers: {}", headersMap);
        
        log.info("Raw payload: {}", payload.toPrettyString());
        log.info("===================================");

        try {
            if (!payload.has("entry")) {
                log.info("No entry in payload");
                return ResponseEntity.ok("IGNORED");
            }

            for (JsonNode entry : payload.get("entry")) {
                String wabaId = entry.has("id") ? entry.get("id").asText() : "unknown";
                log.info("Processing entry for WABA: {}", wabaId);

                if (!entry.has("changes")) continue;

                for (JsonNode change : entry.get("changes")) {
                    String field = change.has("field") ? change.get("field").asText() : "unknown";
                    JsonNode value = change.get("value");

                    log.info("Processing field: {}", field);

                    if ("message_template_status_update".equals(field)) {
                        templateWebhookHandler.handleTemplateStatus(value);
                    }
                    
                    if ("messages".equals(field) && value != null && value.has("statuses")) {
                        handleMessageStatuses(value.get("statuses"));
                    }
                    
                    if ("messages".equals(field) && value != null && value.has("messages")) {
                        handleInboundMessages(value, wabaId);
                    }
                    
                    if ("account_update".equals(field)) {
                        log.info("Account update received: {}", value);
                    }
                }
            }

        } catch (Exception ex) {
            log.error("Webhook parsing error", ex);
        }

        return ResponseEntity.ok("EVENT_RECEIVED");
    }
    
    private void handleMessageStatuses(JsonNode statuses) {
        if (statuses == null || !statuses.isArray()) {
            log.warn("Invalid statuses payload");
            return;
        }

        for (JsonNode status : statuses) {
            try {
                String messageId = status.has("id") ? status.get("id").asText() : null;
                String statusStr = status.has("status") ? status.get("status").asText() : null;
                String timestamp = status.has("timestamp") ? status.get("timestamp").asText() : null;
                String recipientId = status.has("recipient_id") ? status.get("recipient_id").asText() : null;

                if (messageId == null || statusStr == null) {
                    log.warn("Incomplete status data: {}", status);
                    continue;
                }

                log.info("Status update - Message: {}, Status: {}, Recipient: {}, Time: {}", 
                        messageId, statusStr, recipientId, timestamp);
                
                Optional<WhatsAppMessage> msgOpt = messageRepo.findByMetaMessageId(messageId);
                
                if (msgOpt.isPresent()) {
                    WhatsAppMessage msg = msgOpt.get();
                    
                    if (timestamp != null) {
                        LocalDateTime time = LocalDateTime.ofEpochSecond(
                            Long.parseLong(timestamp), 0, ZoneOffset.UTC
                        );
                        
                        if ("delivered".equals(statusStr)) {
                            msg.setDeliveredAt(time);
                            msg.setStatus(WhatsAppMessage.Status.DELIVERED);
                            log.info("Message {} delivered at {}", messageId, time);
                        } else if ("read".equals(statusStr)) {
                            msg.setReadAt(time);
                            msg.setStatus(WhatsAppMessage.Status.READ);
                            log.info("Message {} read at {}", messageId, time);
                        } else if ("sent".equals(statusStr)) {
                            msg.setStatus(WhatsAppMessage.Status.SENT);
                            log.info("Message {} sent", messageId);
                        } else if ("failed".equals(statusStr)) {
                            msg.setStatus(WhatsAppMessage.Status.FAILED);
                            
                            if (status.has("errors")) {
                                JsonNode errors = status.get("errors");
                                StringBuilder errorDetails = new StringBuilder();
                                for (JsonNode error : errors) {
                                    String errorCode = error.has("code") ? error.get("code").asText() : "";
                                    String errorMsg = error.has("message") ? error.get("message").asText() : "";
                                    errorDetails.append("[").append(errorCode).append("] ").append(errorMsg).append("; ");
                                }
                                msg.setFailureReason(errorDetails.toString());
                                log.warn("Message {} failed with errors: {}", messageId, errorDetails);
                            } else {
                                msg.setFailureReason("Failed without specific error");
                                log.warn("Message {} failed (no error details)", messageId);
                            }
                        }
                    } else {
                        if ("sent".equals(statusStr)) {
                            msg.setStatus(WhatsAppMessage.Status.SENT);
                            log.info("Message {} sent (no timestamp)", messageId);
                        } else if ("failed".equals(statusStr)) {
                            msg.setStatus(WhatsAppMessage.Status.FAILED);
                            msg.setFailureReason("Failed (no timestamp)");
                            log.warn("Message {} failed (no timestamp)", messageId);
                        }
                    }
                    
                    messageRepo.save(msg);
                    
                    if (msg.getCampaignId() != null) {
                        updateCampaignStatus(msg.getCampaignId());
                    }
                } else {
                    log.warn("Message not found for meta_message_id: {}", messageId);
                    log.debug("Full status payload: {}", status);
                }
            } catch (Exception e) {
                log.error("Error processing status: {}", e.getMessage(), e);
            }
        }
    }
    
    private void updateCampaignStatus(Long campaignId) {
        try {
            WhatsAppCampaign campaign = campaignRepo.findById(campaignId).orElse(null);
            if (campaign != null) {
                long total = messageRepo.countByCampaignId(campaignId);
                long processed = messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.SENT) +
                                 messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.DELIVERED) +
                                 messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.READ) +
                                 messageRepo.countByCampaignIdAndStatus(campaignId, WhatsAppMessage.Status.FAILED);
                
                campaign.setProcessedContacts((int) processed);
                
                if (processed >= total) {
                    campaign.setStatus(WhatsAppCampaign.Status.COMPLETED);
                } else if (processed > 0) {
                    campaign.setStatus(WhatsAppCampaign.Status.RUNNING);
                }
                
                campaignRepo.save(campaign);
                log.info("Campaign {} progress: {}/{}", campaignId, processed, total);
            }
        } catch (Exception e) {
            log.error("Failed to update campaign status", e);
        }
    }
    
    /**
     * UPDATED: Now stores the actual reply content, type, and timestamp
     */
    private void handleInboundMessages(JsonNode value, String wabaId) {
        if (!value.has("messages")) {
            log.warn("No messages in inbound payload");
            return;
        }
        
        JsonNode messages = value.get("messages");
        JsonNode metadata = value.get("metadata");
        
        if (metadata == null) {
            log.warn("No metadata in inbound payload");
            return;
        }
        
        String phoneNumberId = metadata.has("phone_number_id") ? metadata.get("phone_number_id").asText() : null;
        String displayPhoneNumber = metadata.has("display_phone_number") ? metadata.get("display_phone_number").asText() : null;
        
        log.info("Inbound message received for phone: {}, waba: {}", displayPhoneNumber, wabaId);
        
        for (JsonNode msg : messages) {
            try {
                String from = msg.has("from") ? msg.get("from").asText() : null;
                String msgId = msg.has("id") ? msg.get("id").asText() : null;
                String timestamp = msg.has("timestamp") ? msg.get("timestamp").asText() : null;
                String type = msg.has("type") ? msg.get("type").asText() : "unknown";
                
                if (from == null) {
                    log.warn("Inbound message missing 'from' field");
                    continue;
                }
                
                log.info("Inbound - From: {}, Type: {}, MsgId: {}, Time: {}", 
                        from, type, msgId, timestamp);
                
                // Extract the actual reply content
                String content = extractMessageContent(msg, type);
                log.info("Customer replied: {}", content);
                
                // Find the last outbound message sent to this customer
                List<WhatsAppMessage> outboundMessages = messageRepo
                    .findTopByPhoneNumberAndWabaIdOrderByCreatedAtDesc(from, wabaId);
                
                if (!outboundMessages.isEmpty()) {
                    WhatsAppMessage lastMsg = outboundMessages.get(0);
                    
                    // ===== UPDATED: Store all reply details =====
                    lastMsg.setCustomerReplied(true);
                    lastMsg.setCustomerReplyText(content);
                    lastMsg.setCustomerReplyType(type);
                    
                    if (timestamp != null) {
                        try {
                            LocalDateTime replyTime = LocalDateTime.ofEpochSecond(
                                Long.parseLong(timestamp), 0, ZoneOffset.UTC
                            );
                            lastMsg.setCustomerRepliedAt(replyTime);
                        } catch (Exception e) {
                            lastMsg.setCustomerRepliedAt(LocalDateTime.now());
                        }
                    } else {
                        lastMsg.setCustomerRepliedAt(LocalDateTime.now());
                    }
                    
                    messageRepo.save(lastMsg);
                    log.info("✅ Customer {} replied to message {}: '{}'", 
                        from, lastMsg.getId(), content);
                } else {
                    log.info("No outbound message found for customer {}", from);
                    
                    // Optional: Store as new inbound message if you want to track standalone replies
                    // You could create a separate entity for inbound messages
                }
                
            } catch (Exception e) {
                log.error("Error processing inbound message: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Extracts message content based on message type
     */
    private String extractMessageContent(JsonNode msg, String type) {
        try {
            switch (type) {
                case "text":
                    return msg.has("text") && msg.get("text").has("body") 
                        ? msg.get("text").get("body").asText() 
                        : "[No text content]";
                        
                case "image":
                    if (msg.has("image") && msg.get("image").has("caption")) {
                        return "[Image with caption: " + msg.get("image").get("caption").asText() + "]";
                    }
                    return "[Image]";
                    
                case "audio":
                    return "[Audio message]";
                    
                case "video":
                    return "[Video message]";
                    
                case "document":
                    if (msg.has("document") && msg.get("document").has("filename")) {
                        return "[Document: " + msg.get("document").get("filename").asText() + "]";
                    }
                    return "[Document]";
                    
                case "button":
                    return msg.has("button") && msg.get("button").has("text") 
                        ? "[Button: " + msg.get("button").get("text").asText() + "]"
                        : "[Button reply]";
                        
                case "interactive":
                    if (msg.has("interactive") && msg.get("interactive").has("button_reply")) {
                        JsonNode buttonReply = msg.get("interactive").get("button_reply");
                        String buttonText = buttonReply.has("title") ? buttonReply.get("title").asText() : "";
                        String buttonId = buttonReply.has("id") ? buttonReply.get("id").asText() : "";
                        return "[Interactive button: " + buttonText + " (ID: " + buttonId + ")]";
                    } else if (msg.has("interactive") && msg.get("interactive").has("list_reply")) {
                        JsonNode listReply = msg.get("interactive").get("list_reply");
                        String listTitle = listReply.has("title") ? listReply.get("title").asText() : "";
                        return "[List selection: " + listTitle + "]";
                    }
                    return "[Interactive]";
                    
                case "location":
                    if (msg.has("location")) {
                        JsonNode loc = msg.get("location");
                        String lat = loc.has("latitude") ? loc.get("latitude").asText() : "?";
                        String lon = loc.has("longitude") ? loc.get("longitude").asText() : "?";
                        return "[Location: " + lat + ", " + lon + "]";
                    }
                    return "[Location]";
                    
                case "contacts":
                    return "[Contact shared]";
                    
                case "reaction":
                    if (msg.has("reaction") && msg.get("reaction").has("emoji")) {
                        return "[Reaction: " + msg.get("reaction").get("emoji").asText() + "]";
                    }
                    return "[Reaction]";
                    
                default:
                    return "[Unknown type: " + type + "]";
            }
        } catch (Exception e) {
            log.warn("Failed to extract message content: {}", e.getMessage());
            return "[Content extraction failed]";
        }
    }
}