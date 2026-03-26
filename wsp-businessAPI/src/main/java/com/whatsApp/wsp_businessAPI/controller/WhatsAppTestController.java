package com.whatsApp.wsp_businessAPI.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.dto.WhatsAppTestSendRequest;
import com.whatsApp.wsp_businessAPI.service.WhatsAppTestMessageService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/whatsapp/test")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTestController {

    private final WhatsAppTestMessageService testMessageService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<?>> sendTestMessage(
            @RequestBody @Valid WhatsAppTestSendRequest req
    ) {

        log.info("App Review test message request received. To={}", req.getTo());

        Map<String, Object> response =
                testMessageService.sendTestMessage(
                        req.getPhoneNumberId(),
                        req.getTo()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Test WhatsApp message sent (App Review)",
                        response
                )
        );
    }
}