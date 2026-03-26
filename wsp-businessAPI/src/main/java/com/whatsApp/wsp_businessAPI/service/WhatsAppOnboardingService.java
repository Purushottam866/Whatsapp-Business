package com.whatsApp.wsp_businessAPI.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.whatsApp.wsp_businessAPI.dto.EmbeddedSignupRequest;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.exceptions.WhatsAppApiException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppOnboardingService {

    private final MetaApiClient metaApiClient;
    private final WhatsAppAccountRepository repository;
    private final WhatsAppBusinessProfileService profileService;
    private final CurrentTenantService currentTenantService;

    @Transactional
    public ApiResponse<Map<String, Object>> completeEmbeddedSignup(EmbeddedSignupRequest req) {

        Long tenantId = currentTenantService.getTenantId();

        log.info("========== EMBEDDED SIGNUP START ==========");
        log.info("Tenant: {}", tenantId);
        log.info("WABA: {}", req.getWabaId());
        log.info("Phone Number ID: {}", req.getPhoneNumberId());

        /* =======================================================
           STEP 1 — TOKEN EXCHANGE
        ======================================================= */

        Map<String, Object> tokenResp =
                metaApiClient.exchangeAuthorizationCode(req.getAuthorizationCode());

        String accessToken = (String) tokenResp.get("access_token");

        if (accessToken == null) {
            throw new WhatsAppApiException(
                    "Access token missing in Meta response",
                    "META_TOKEN_EXCHANGE_FAILED" 
            );
        }

        log.info("System user token received");

        /* =======================================================
           STEP 2 — GLOBAL WABA OWNERSHIP CHECK (CRITICAL FIX)
        ======================================================= */

        Optional<WhatsAppAccount> existingGlobal =
                repository.findByWabaId(req.getWabaId());

        if (existingGlobal.isPresent()) {

            WhatsAppAccount existing = existingGlobal.get();

            // WABA already belongs to another tenant
            if (!existing.getTenantId().equals(tenantId)) {
                throw new ValidationException(
                        "This WhatsApp number is already connected to another organization",
                        "WABA_ALREADY_CONNECTED"
                );
            }

            log.info("Existing WABA found for same tenant — updating token only");
        }

        WhatsAppAccount account = existingGlobal.orElse(new WhatsAppAccount());

        /* =======================================================
           STEP 3 — PHONE VERIFICATION STATUS
        ======================================================= */

        String verificationStatus = "UNKNOWN";

        try {
            verificationStatus = metaApiClient.getPhoneRegistrationStatus(
                    req.getPhoneNumberId(),
                    accessToken
            );
            log.info("Meta verification status: {}", verificationStatus);
        } catch (Exception ex) {
            log.warn("Unable to fetch verification status: {}", ex.getMessage());
        }

        boolean registerAttempted = false;
        boolean registerSuccess = false;

        /* =======================================================
           STEP 4 — REGISTER CLOUD API SENDER
        ======================================================= */

        if ("VERIFIED".equalsIgnoreCase(verificationStatus)
                && (account.getPhoneRegistered() == null || !account.getPhoneRegistered())) {

            try {

                String pin = String.valueOf(
                        ThreadLocalRandom.current().nextInt(100000, 999999)
                );

                log.info("Activating Cloud API sender");

                metaApiClient.registerPhoneNumber(
                        req.getPhoneNumberId(),
                        accessToken,
                        pin
                );

                account.setPhoneRegistered(true);
                registerAttempted = true;
                registerSuccess = true;

                log.info("Cloud API sender activation successful");

            } catch (Exception ex) {
                registerAttempted = true;
                registerSuccess = false;
                log.warn("Phone register failed: {}", ex.getMessage());
            }
        }

        /* =======================================================
           STEP 5 — FETCH PHONE METADATA
        ======================================================= */

        String displayNumber = null;
        String qualityRating = null;

        try {
            Map<String, Object> phoneData =
                    metaApiClient.fetchPhoneNumberDetails(
                            req.getPhoneNumberId(),
                            accessToken
                    );

            displayNumber = (String) phoneData.get("display_phone_number");
            qualityRating = (String) phoneData.get("quality_rating");

        } catch (Exception ex) {
            log.warn("Failed to fetch phone metadata: {}", ex.getMessage());
        }

        /* =======================================================
           STEP 6 — DETERMINE ACCOUNT STATUS
        ======================================================= */

        WhatsAppAccount.Status accountStatus =
                Boolean.TRUE.equals(account.getPhoneRegistered())
                        ? WhatsAppAccount.Status.ACTIVE
                        : WhatsAppAccount.Status.PENDING;

        /* =======================================================
           STEP 7 — SAVE ACCOUNT (TENANT BINDING HAPPENS HERE)
        ======================================================= */

        account.setTenantId(tenantId);
        account.setBusinessId(req.getBusinessId());
        account.setWabaId(req.getWabaId());
        account.setPhoneNumberId(req.getPhoneNumberId());
        account.setDisplayPhoneNumber(displayNumber);
        account.setQualityRating(qualityRating);
        account.setSystemUserToken(accessToken);
        account.setTokenLastValidatedAt(LocalDateTime.now());
        account.setMetaApiVersion("v22.0");
        account.setWebhookConfigured(false);
        account.setStatus(accountStatus);
        account.setIsTestAccount(false);

        WhatsAppAccount saved = repository.save(account);

        log.info("WhatsApp account saved. id={}, tenant={}, status={}",
                saved.getId(),
                saved.getTenantId(),
                saved.getStatus()
        );

        /* =======================================================
           STEP 8 — WEBHOOK SUBSCRIPTION
        ======================================================= */

        try {
            metaApiClient.subscribeWabaToWebhooks(req.getWabaId(), accessToken);
            saved.setWebhookConfigured(true);
            repository.save(saved);

            try {
                profileService.syncProfileName(saved);
            } catch (Exception ex) {
                log.warn("Profile sync skipped: {}", ex.getMessage());
            }

        } catch (Exception ex) {
            log.warn("Webhook subscription failed: {}", ex.getMessage());
        }

        log.info("========== EMBEDDED SIGNUP END ==========");

        return ApiResponse.success(
                saved.getStatus() == WhatsAppAccount.Status.ACTIVE
                        ? "WhatsApp connected and messaging activated"
                        : "WhatsApp connected. Verify phone in Manager. Messaging will activate automatically.",
                Map.of(
                        "accountId", saved.getId(),
                        "tenantId", saved.getTenantId(),
                        "wabaId", saved.getWabaId(),
                        "phoneNumberId", saved.getPhoneNumberId(),
                        "displayPhoneNumber", saved.getDisplayPhoneNumber(),
                        "verificationStatus", verificationStatus,
                        "registerAttempted", registerAttempted,
                        "registerSuccess", registerSuccess,
                        "phoneRegistered", saved.getPhoneRegistered(),
                        "status", saved.getStatus()
                )
        );
    }

    
    public ApiResponse<?> getConnectedAccount(Long tenantId) {
        
        log.info("Fetching connected WhatsApp account for tenant: {}", tenantId);
        
        Optional<WhatsAppAccount> accountOpt = repository.findByTenantId(tenantId);
        
        if (accountOpt.isEmpty()) {
            return ApiResponse.success(
                    "No WhatsApp account connected",
                    Map.of("connected", false)
            );
        }
        
        WhatsAppAccount account = accountOpt.get();
        
        // Use HashMap instead of Map.of() for more than 10 entries
        Map<String, Object> accountInfo = new HashMap<>();
        accountInfo.put("connected", true);
        accountInfo.put("accountId", account.getId());
        accountInfo.put("wabaId", account.getWabaId());
        accountInfo.put("phoneNumberId", account.getPhoneNumberId());
        accountInfo.put("displayPhoneNumber", account.getDisplayPhoneNumber());
        accountInfo.put("profileName", account.getProfileName());
        accountInfo.put("status", account.getStatus());
        accountInfo.put("qualityRating", account.getQualityRating());
        accountInfo.put("webhookConfigured", account.getWebhookConfigured());
        accountInfo.put("phoneRegistered", account.getPhoneRegistered());
        accountInfo.put("businessId", account.getBusinessId());
        accountInfo.put("createdAt", account.getCreatedAt());
        
        return ApiResponse.success(
                "WhatsApp account details fetched successfully",
                accountInfo
        );
    }
}


