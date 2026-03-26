package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.service.WhatsAppTemplateSubmissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/whatsapp/templates")
@RequiredArgsConstructor
public class WhatsAppTemplateSubmissionController {

    private final WhatsAppTemplateSubmissionService service;

    // JWT REQUIRED
    @PostMapping("/{templateId}/submit")
    public ResponseEntity<?> submit(@PathVariable Long templateId) {
        return ResponseEntity.ok(service.submitTemplate(templateId));
    }
}
