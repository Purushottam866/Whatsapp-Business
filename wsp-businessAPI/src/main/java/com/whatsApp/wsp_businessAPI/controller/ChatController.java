package com.whatsApp.wsp_businessAPI.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.service.ChatService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    /**
     * Get all conversations for a specific WhatsApp number
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<?>> getConversations(
            @RequestParam String wabaId,
            @RequestParam String phoneNumberId) {
        
        try {
            var conversations = chatService.getConversations(wabaId, phoneNumberId);
            return ResponseEntity.ok(
                ApiResponse.success("Conversations fetched successfully", conversations)
            );
        } catch (Exception e) {
            log.error("Failed to fetch conversations: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Failed to fetch conversations", "CHAT_ERROR", e.getMessage()));
        }
    }

    /**
     * Get chat history with a specific contact from a specific sender number
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<?>> getChatHistory(
            @RequestParam String phoneNumberId,
            @RequestParam String recipientPhone) {
        
        try {
            var history = chatService.getChatHistory(phoneNumberId, recipientPhone);
            return ResponseEntity.ok(
                ApiResponse.success("Chat history fetched successfully", history)
            );
        } catch (Exception e) {
            log.error("Failed to fetch chat history: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Failed to fetch chat history", "CHAT_ERROR", e.getMessage()));
        }
    }

    /**
     * Send a text message
     */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<?>> sendTextMessage(
            @RequestParam String wabaId,
            @RequestParam String phoneNumberId,
            @RequestParam String to,
            @RequestBody Map<String, String> request) {
        
        String text = request.get("text");
        
        try {
            var result = chatService.sendTextMessage(wabaId, phoneNumberId, to, text);
            
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(
                    ApiResponse.success("Message sent successfully", result)
                );
            } else {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Failed to send message", "SEND_ERROR", result.get("error").toString()));
            }
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Failed to send message", "SEND_ERROR", e.getMessage()));
        }
    }

    /**
     * Update contact name
     */
    @PutMapping("/contact/name/{phoneNumber}")
    public ResponseEntity<ApiResponse<?>> updateContactName(
            @PathVariable String phoneNumber,
            @RequestBody Map<String, String> request) {
        
        String contactName = request.get("contactName");
        
        try {
            var result = chatService.updateContactName(phoneNumber, contactName);
            return ResponseEntity.ok(
                ApiResponse.success("Contact name updated successfully", result)
            );
        } catch (Exception e) {
            log.error("Failed to update contact name: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Failed to update contact name", "UPDATE_ERROR", e.getMessage()));
        }
    }

    /**
     * Get contact details
     */
    @GetMapping("/contact/details/{phoneNumber}")
    public ResponseEntity<ApiResponse<?>> getContactDetails(@PathVariable String phoneNumber) {
        
        try {
            var details = chatService.getContactDetails(phoneNumber);
            return ResponseEntity.ok(
                ApiResponse.success("Contact details fetched successfully", details)
            );
        } catch (Exception e) {
            log.error("Failed to fetch contact details: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Failed to fetch contact details", "CHAT_ERROR", e.getMessage()));
        }
    }
}