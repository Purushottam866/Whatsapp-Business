package com.whatsApp.wsp_businessAPI.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.exceptions.WhatsAppApiException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTokenService {

    private final MetaApiClient metaApiClient;
    private final WhatsAppAccountRepository accountRepository;

    private static final long VALIDATION_CACHE_MINUTES = 10;

    // ✅ NEW: Get specific account by phone number ID
    public WhatsAppAccount validateAndGetAccountByPhoneNumberId(Long tenantId, String wabaId, String phoneNumberId) {
        
        WhatsAppAccount account = accountRepository
                .findByTenantIdAndWabaIdAndPhoneNumberId(tenantId, wabaId, phoneNumberId)
                .orElseThrow(() ->
                        new ValidationException(
                                "WhatsApp account not found",
                                "No WhatsApp Business account found with phone number ID: " + phoneNumberId
                        )
                );

        return validateAccount(account);
    }

    // ✅ MODIFIED: Original method now calls the new method with first account
    public WhatsAppAccount validateAndGetActiveAccount(Long tenantId, String wabaId) {
        
        WhatsAppAccount account = accountRepository
                .findFirstByTenantIdAndWabaIdOrderByIdAsc(tenantId, wabaId)
                .orElseThrow(() ->
                        new ValidationException(
                                "WhatsApp account not connected",
                                "No connected WhatsApp Business account found"
                        )
                );

        return validateAccount(account);
    }

    // ✅ NEW: Shared validation logic
    private WhatsAppAccount validateAccount(WhatsAppAccount account) {

        if (account.getStatus() != WhatsAppAccount.Status.ACTIVE) {
            throw new ValidationException(
                    "WhatsApp account not active",
                    "Account is disconnected or revoked"
            );
        }

        if (account.getSystemUserToken() == null || account.getSystemUserToken().isBlank()) {
            throw new ValidationException(
                    "WhatsApp access token missing",
                    "Please reconnect your WhatsApp Business account"
            );
        }

        // Cache validation check
        if (account.getTokenLastValidatedAt() != null &&
                account.getTokenLastValidatedAt()
                        .isAfter(LocalDateTime.now().minusMinutes(VALIDATION_CACHE_MINUTES))) {

            log.info("Skipping debug_token — recently validated for account {}", account.getPhoneNumberId());
            return account;
        }

        // Meta token validation
        try {
            Map<String, Object> debug = metaApiClient.debugToken(account.getSystemUserToken());

            if (debug == null || !debug.containsKey("data")) {
                log.warn("debug_token returned empty response for account {}", account.getPhoneNumberId());
                return account;
            }

            Map<String, Object> data = (Map<String, Object>) debug.get("data");
            Boolean isValid = (Boolean) data.get("is_valid");

            if (Boolean.FALSE.equals(isValid)) {
                log.error("WhatsApp token invalidated by Meta for account {}", account.getPhoneNumberId());
                account.setStatus(WhatsAppAccount.Status.REVOKED);
                accountRepository.save(account);
                throw new ValidationException(
                        "WhatsApp connection revoked",
                        "Please reconnect your WhatsApp Business account"
                );
            }

            account.setTokenLastValidatedAt(LocalDateTime.now());
            accountRepository.save(account);
            log.info("WhatsApp token validated successfully for account {}", account.getPhoneNumberId());

            return account;

        } catch (org.springframework.web.client.ResourceAccessException netEx) {
            log.warn("Meta debug_token unreachable. Allowing send. Reason: {}", netEx.getMessage());
            return account;
        } catch (org.springframework.web.client.HttpClientErrorException httpEx) {
            log.error("Meta rejected token for account {} : {}", account.getPhoneNumberId(), httpEx.getResponseBodyAsString());
            account.setStatus(WhatsAppAccount.Status.REVOKED);
            accountRepository.save(account);
            throw new ValidationException(
                    "WhatsApp connection revoked",
                    "Please reconnect your WhatsApp Business account"
            );
        } catch (Exception ex) {
            log.warn("Token validation skipped due to unexpected error: {}", ex.getMessage());
            return account;
        }
    }
}