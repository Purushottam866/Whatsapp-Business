package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.dto.BulkSendWhatsAppMessageRequest;
import com.whatsApp.wsp_businessAPI.dto.SendWhatsAppMessageRequest;
import com.whatsApp.wsp_businessAPI.service.WhatsAppMessageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/whatsapp/messages")
@RequiredArgsConstructor
public class WhatsAppMessageController {

    private final WhatsAppMessageService service;

    @PostMapping("/send")
    public ResponseEntity<?> send(
            @RequestBody @Valid SendWhatsAppMessageRequest req
    ) {
        return ResponseEntity.ok(service.sendSingle(req));
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<?> sendBulk(
            @RequestBody @Valid BulkSendWhatsAppMessageRequest req 
    ) {
        return ResponseEntity.ok(service.sendBulk(req));
    }
    
    @GetMapping("/campaign/{campaignId}/status")
    public ResponseEntity<?> getCampaignStatus(@PathVariable Long campaignId) {
        return ResponseEntity.ok(service.getCampaignStatus(campaignId));
    }
}