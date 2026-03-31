package com.whatsApp.wsp_businessAPI.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final WhatsAppMessageRepository messageRepo;
    private final WhatsAppAccountRepository accountRepo;
    private final MetaApiClient metaApiClient;
    private final CurrentTenantService currentTenantService;
    private final WhatsAppTokenService tokenService;

    private static final int MAX_CONVERSATIONS = 50;

    /**
     * OPTIMIZED: Get all conversations in a single query
     */
    public List<Map<String, Object>> getConversations(String wabaId, String phoneNumberId) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching conversations for tenant: {}, waba: {}, sending from phone: {}", tenantId, wabaId, phoneNumberId);
        
        long startTime = System.currentTimeMillis();
        
        // Verify account belongs to this tenant
        WhatsAppAccount account = tokenService.validateAndGetAccountByPhoneNumberId(tenantId, wabaId, phoneNumberId);
        log.info("Account validated: {} ({})", account.getDisplayPhoneNumber(), account.getPhoneNumberId());
        
        // SINGLE QUERY to get all conversations
        List<Object[]> results = messageRepo.findConversationsOptimized(tenantId, phoneNumberId, MAX_CONVERSATIONS);
        
        List<Map<String, Object>> conversations = new ArrayList<>();
        
        for (Object[] row : results) {
            try {
                // Extract values with proper type handling
                String phoneNumber = row[0] != null ? row[0].toString() : "";
                String contactName = row[1] != null ? row[1].toString() : null;
                Object lastMsgTimeObj = row[2];
                String lastMessage = row[3] != null ? row[3].toString() : "No messages";
                Boolean isReceived = row[4] != null ? (Integer.valueOf(row[4].toString()) == 1) : false;
                
                // Convert MySQL timestamp to LocalDateTime
                LocalDateTime lastMessageTime = null;
                if (lastMsgTimeObj != null) {
                    if (lastMsgTimeObj instanceof java.sql.Timestamp) {
                        lastMessageTime = ((java.sql.Timestamp) lastMsgTimeObj).toLocalDateTime();
                    } else if (lastMsgTimeObj instanceof LocalDateTime) {
                        lastMessageTime = (LocalDateTime) lastMsgTimeObj;
                    } else if (lastMsgTimeObj instanceof String) {
                        lastMessageTime = LocalDateTime.parse((String) lastMsgTimeObj);
                    }
                }
                
                // If contactName is null, use phone number
                if (contactName == null || contactName.isEmpty()) {
                    contactName = phoneNumber;
                }
                
                Map<String, Object> conv = new LinkedHashMap<>();
                conv.put("phoneNumber", phoneNumber);
                conv.put("contactName", contactName);
                conv.put("lastMessage", lastMessage);
                conv.put("lastMessageTime", lastMessageTime);
                conv.put("lastMessageDirection", isReceived ? "received" : "sent");
                conv.put("unreadCount", 0);
                
                conversations.add(conv);
                 
            } catch (Exception e) {
                log.error("Error processing row: {}", e.getMessage());
            }
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Found {} conversations for sender: {} in {} ms", conversations.size(), account.getDisplayPhoneNumber(), elapsedTime);
        
        return conversations;
    }

    /**
     * Get chat history with a specific contact (optimized)
     */
    public List<Map<String, Object>> getChatHistory(String phoneNumberId, String recipientPhone) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching chat history for sender: {}, recipient: {}, tenant: {}", phoneNumberId, recipientPhone, tenantId);
        
        long startTime = System.currentTimeMillis();
        
        // Single query to get all messages for this conversation
        List<WhatsAppMessage> messages = messageRepo
            .findByTenantIdAndPhoneNumberIdAndPhoneNumberOrderByCreatedAtAsc(
                tenantId, phoneNumberId, recipientPhone);
        
        if (messages.isEmpty()) {
            log.info("No messages found");
            return new ArrayList<>();
        }
        
        // Get contact name from first message that has it
        String contactName = recipientPhone;
        for (WhatsAppMessage msg : messages) {
            if (msg.getContactName() != null && !msg.getContactName().isEmpty()) {
                contactName = msg.getContactName();
                break;
            }
        }
        
        List<Map<String, Object>> chatHistory = new ArrayList<>();
        
        for (WhatsAppMessage msg : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", msg.getId());
            entry.put("direction", msg.getCustomerReplied() ? "received" : "sent");
            entry.put("message", msg.getCustomerReplyText() != null ? msg.getCustomerReplyText() : msg.getTemplateName());
            entry.put("type", msg.getCustomerReplyType() != null ? msg.getCustomerReplyType() : "template");
            entry.put("timestamp", msg.getCustomerRepliedAt() != null ? msg.getCustomerRepliedAt() : msg.getCreatedAt());
            entry.put("status", msg.getStatus());
            
            if (msg.getCustomerReplied()) {
                entry.put("senderName", contactName);
            } else {
                entry.put("senderName", "Me");
            }
            
            chatHistory.add(entry);
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Found {} messages in {} ms", chatHistory.size(), elapsedTime);
        
        return chatHistory;
    }

    /**
     * Send a regular text message (non-template)
     */
    @Transactional
    public Map<String, Object> sendTextMessage(String wabaId, String phoneNumberId, String to, String text) {
        Long tenantId = currentTenantService.getTenantId();
        log.info("Sending text message from phone: {} to: {}, tenant: {}", phoneNumberId, to, tenantId);
        
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("EMPTY_MESSAGE", "Message text cannot be empty");
        }
        
        WhatsAppAccount account = tokenService.validateAndGetAccountByPhoneNumberId(tenantId, wabaId, phoneNumberId);
        
        Map<String, Object> metaResponse = metaApiClient.sendTextMessage(
            phoneNumberId, account.getSystemUserToken(), to, text);
        
        if (metaResponse.containsKey("error")) {
            log.error("Failed to send: {}", metaResponse.get("error"));
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to send message");
            errorResponse.put("error", metaResponse.get("error"));
            return errorResponse;
        }
        
        WhatsAppMessage outgoingMsg = WhatsAppMessage.builder()
            .tenantId(tenantId)
            .wabaId(wabaId)
            .phoneNumberId(phoneNumberId)
            .phoneNumber(to)
            .templateName("Text Message")
            .templateLanguage("en_US")
            .templateCategory("UTILITY")
            .status(WhatsAppMessage.Status.SENT)
            .customerReplied(false)
            .customerReplyText(text)
            .customerReplyType("text")
            .customerRepliedAt(LocalDateTime.now())
            .build();
        
        messageRepo.save(outgoingMsg);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("messageId", metaResponse.get("messages"));
        response.put("to", to);
        response.put("text", text);
        response.put("sentAt", LocalDateTime.now());
        
        return response;
    }

    /**
     * Update contact name
     */
    @Transactional
    public Map<String, Object> updateContactName(String phoneNumber, String contactName) {
        Long tenantId = currentTenantService.getTenantId();
        
        if (contactName == null || contactName.trim().isEmpty()) {
            throw new ValidationException("INVALID_NAME", "Contact name cannot be empty");
        }
        
        int updated = messageRepo.updateContactNameByPhoneNumberAndTenantId(tenantId, phoneNumber, contactName.trim());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("phoneNumber", phoneNumber);
        response.put("contactName", contactName);
        response.put("updatedRecords", updated);
        
        return response;
    }
    
    /**
     * Get contact details
     */
    public Map<String, Object> getContactDetails(String phoneNumber) {
        Long tenantId = currentTenantService.getTenantId();
        
        Optional<WhatsAppMessage> latestMsg = messageRepo
            .findTopByPhoneNumberAndTenantIdOrderByCreatedAtDesc(phoneNumber, tenantId);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("phoneNumber", phoneNumber);
        
        if (latestMsg.isPresent()) {
            response.put("contactName", latestMsg.get().getContactName() != null ? 
                latestMsg.get().getContactName() : phoneNumber);
        } else {
            response.put("contactName", phoneNumber);
        }
        
        return response;
    }
}