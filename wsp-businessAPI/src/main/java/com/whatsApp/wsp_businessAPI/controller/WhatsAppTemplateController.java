package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.whatsApp.wsp_businessAPI.dto.CreateTemplateRequest;
import com.whatsApp.wsp_businessAPI.service.WhatsAppTemplateService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/whatsapp/templates")
@RequiredArgsConstructor
public class WhatsAppTemplateController {

    private final WhatsAppTemplateService service;
    private final ObjectMapper objectMapper;

    // JWT REQUIRED + FILE SUPPORT
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestPart("data") String json,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) throws Exception {

        CreateTemplateRequest req =
                objectMapper.readValue(json, CreateTemplateRequest.class);

        return ResponseEntity.ok(
                ApiResponse.created(
                        "Template created and saved draft",
                        service.createTemplate(req, file)
                )
        );
    }
}

