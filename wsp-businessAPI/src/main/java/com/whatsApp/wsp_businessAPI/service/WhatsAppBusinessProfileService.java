package com.whatsApp.wsp_businessAPI.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppAccountRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppBusinessProfileService {

    private final WhatsAppAccountRepository accountRepository;
    private final MetaApiClient metaApiClient;

    @Transactional
    public void syncProfileName(WhatsAppAccount account) {

        try {

            Map<String, Object> resp =
                    metaApiClient.fetchWhatsAppBusinessProfile(
                            account.getPhoneNumberId(),
                            account.getSystemUserToken()
                    );

            if (resp == null || !resp.containsKey("data")) {
                log.warn("WhatsApp profile response empty");
                return;
            }

            List<Map<String, Object>> data =
                    (List<Map<String, Object>>) resp.get("data");

            if (data.isEmpty()) {
                log.warn("WhatsApp profile not configured yet");
                return;
            }

            Map<String, Object> profile = data.get(0);

            // priority: description -> about -> phone
            String profileName =
                    profile.get("description") != null
                            ? profile.get("description").toString()
                            : (profile.get("about") != null
                            ? profile.get("about").toString()
                            : account.getDisplayPhoneNumber());

            // prevent long garbage text
            if (profileName.length() > 60) {
                profileName = profileName.substring(0, 60);
            }

            account.setProfileName(profileName);
            accountRepository.save(account);

            log.info("WhatsApp profile synced: {}", profileName);

        } catch (Exception ex) {
            log.warn("Unable to sync WhatsApp profile yet: {}", ex.getMessage());
        }
    }
}
