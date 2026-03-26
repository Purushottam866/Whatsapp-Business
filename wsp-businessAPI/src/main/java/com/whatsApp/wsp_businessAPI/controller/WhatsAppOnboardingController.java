package com.whatsApp.wsp_businessAPI.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.dto.EmbeddedSignupRequest;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.service.CurrentTenantService;
import com.whatsApp.wsp_businessAPI.service.WhatsAppOnboardingService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/whatsapp/onboarding")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppOnboardingController {
	
	
	private final WhatsAppAccountRepository repository;
    private final WhatsAppOnboardingService service;
    private final CurrentTenantService currentTenantService;

    @PostMapping("/embedded-complete")
    public ResponseEntity<ApiResponse<?>> complete(
            @RequestBody EmbeddedSignupRequest req
    ) {

        // 🔐 user must be logged in
        Long tenantId = currentTenantService.getTenantId();
        Long userId   = currentTenantService.getUserId();

        log.info("📥 Embedded signup callback received");
        log.info("UserId: {}", userId);
        log.info("TenantId: {}", tenantId);
        log.info("WABA ID: {}", req.getWabaId());
        log.info("PHONE NUMBER ID: {}", req.getPhoneNumberId());
        log.info("BUSINESS ID: {}", req.getBusinessId());

        ApiResponse<?> response = service.completeEmbeddedSignup(req);

        return ResponseEntity
                .status(response.getStatusCode())
                .body(response);
    }
    
    // ✅ SINGLE ENDPOINT that returns both primary account and all numbers
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWhatsAppInfo() {
        
        Long tenantId = currentTenantService.getTenantId();
        log.info("Fetching WhatsApp info for tenant: {}", tenantId);
        
        List<WhatsAppAccount> allAccounts = repository.findAllByTenantId(tenantId);
        
        Map<String, Object> response = new HashMap<>();
        
        if (allAccounts.isEmpty()) {
            response.put("connected", false);
            response.put("message", "No WhatsApp account connected");
            response.put("phoneNumbers", List.of());
        } else {
            // Primary account (first one)
            WhatsAppAccount primary = allAccounts.get(0);
            
            Map<String, Object> primaryInfo = new HashMap<>();
            primaryInfo.put("accountId", primary.getId());
            primaryInfo.put("wabaId", primary.getWabaId());
            primaryInfo.put("businessId", primary.getBusinessId());
            primaryInfo.put("status", primary.getStatus());
            primaryInfo.put("webhookConfigured", primary.getWebhookConfigured());
            primaryInfo.put("metaApiVersion", primary.getMetaApiVersion());
            primaryInfo.put("createdAt", primary.getCreatedAt());
            
            // All phone numbers list
            List<Map<String, Object>> phoneNumbers = allAccounts.stream().map(account -> {
                Map<String, Object> phoneInfo = new HashMap<>();
                phoneInfo.put("accountId", account.getId());
                phoneInfo.put("phoneNumberId", account.getPhoneNumberId());
                phoneInfo.put("displayPhoneNumber", account.getDisplayPhoneNumber());
                phoneInfo.put("profileName", account.getProfileName());
                phoneInfo.put("qualityRating", account.getQualityRating());
                phoneInfo.put("status", account.getStatus());
                phoneInfo.put("phoneRegistered", account.getPhoneRegistered());
                phoneInfo.put("isPrimary", account.getId().equals(primary.getId()));
                return phoneInfo;
            }).collect(Collectors.toList());
            
            response.put("connected", true);
            response.put("primary", primaryInfo);
            response.put("phoneNumbers", phoneNumbers);
            response.put("totalNumbers", phoneNumbers.size());
        }
        
        return ResponseEntity.ok(
                ApiResponse.success("WhatsApp info fetched successfully", response)
        );
    }
}