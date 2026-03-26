package com.whatsApp.wsp_businessAPI.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.dto.ObaApplicationRequest;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ObaApplicationService {

    private final WhatsAppAccountRepository accountRepository;
    private final MetaApiClient metaApiClient;
    private final CurrentTenantService currentTenantService;

    @Transactional
    public Map<String, Object> submitApplication(ObaApplicationRequest request) {

        Long tenantId = currentTenantService.getTenantId();

        // 🔐 Find WhatsApp account belonging to THIS user
        WhatsAppAccount account = accountRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ValidationException(
                        "WhatsApp not connected",
                        "Connect WhatsApp before applying for blue tick"
                ));

        if (account.getStatus() != WhatsAppAccount.Status.ACTIVE) {
            throw new ValidationException(
                    "WhatsApp not active",
                    "Your WhatsApp number is not fully activated yet"
            );
        }

        String phoneNumberId = account.getPhoneNumberId();
        String token = account.getSystemUserToken();

        // Call Meta
        Map<String, Object> metaResp = metaApiClient.submitObaApplication(
                phoneNumberId,
                token,
                request
        );

        log.info("OBA applied. tenant={}, phoneNumberId={}", tenantId, phoneNumberId);

        return Map.of(
                "phoneNumberId", phoneNumberId,
                "metaResponse", metaResp
        );
    }

    public Map<String, Object> getObaStatus() {

        Long tenantId = currentTenantService.getTenantId();

        WhatsAppAccount account = accountRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ValidationException(
                        "WhatsApp not connected",
                        "Connect WhatsApp first"
                ));

        return metaApiClient.getObaStatus(
                account.getPhoneNumberId(),
                account.getSystemUserToken()
        );
    }
}
