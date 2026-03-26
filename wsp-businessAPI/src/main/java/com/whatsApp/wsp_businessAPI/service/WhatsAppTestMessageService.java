package com.whatsApp.wsp_businessAPI.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;  

@Service
@Slf4j
public class WhatsAppTestMessageService {

    private final RestTemplate restTemplate = new RestTemplate();

    // 🔐 THIS comes from Meta → WhatsApp → API Setup
    @Value("${meta.test.access-token}")
    private String accessToken;

    public Map<String, Object> sendTestMessage(
            String phoneNumberId,
            String toNumber
    ) {
    	
    	System.out.println("Phone number id for test wsp msg " + phoneNumberId);

        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalArgumentException("Test phone number ID is required");
        } 

        if (toNumber == null || toNumber.isBlank()) {
            throw new IllegalArgumentException("Recipient WhatsApp number is required");
        }

        log.info("Sending WhatsApp test message via phoneNumberId={} to={}",
                phoneNumberId, toNumber);

        String url = "https://graph.facebook.com/v22.0/"
                + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", toNumber,
                "type", "template",
                "template", Map.of(
                        "name", "hello_world",
                        "language", Map.of("code", "en_US")
                )
        );

        try {
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            url,
                            new HttpEntity<>(payload, headers),
                            Map.class
                    );

            log.info("Meta response: {}", response.getBody());

            return Map.of(
                    "phone_number_id", phoneNumberId,
                    "to", toNumber,
                    "template", "hello_world",
                    "meta_response", response.getBody()
            );

        } catch (HttpClientErrorException ex) {
            log.error("Meta API error: {}", ex.getResponseBodyAsString());
            throw ex;
        }
    }
}





//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class WhatsAppTestMessageService {
//
//    private final RestTemplate restTemplate = new RestTemplate();
//    private final WhatsAppAccountRepository accountRepository;
//
//    public Map<String, Object> sendTestMessage(
//            String phoneNumberId,
//            String toNumber
//    ) {
//
//        if (toNumber == null || toNumber.isBlank()) {
//            throw new IllegalArgumentException("Recipient WhatsApp number is required");
//        }
//
//        if (phoneNumberId == null || phoneNumberId.isBlank()) {
//            throw new IllegalArgumentException("Phone Number ID is required");
//        }
//
//        // 🔑 Fetch LAST ACTIVE onboarded account (App Review simplicity)
//        WhatsAppAccount account =
//                accountRepository.findAllByStatus(WhatsAppAccount.Status.ACTIVE)
//                        .stream()
//                        .findFirst()
//                        .orElseThrow(() ->
//                                new IllegalStateException(
//                                        "No active WhatsApp account found. Complete onboarding first."
//                                )
//                        );
//
//        String accessToken = account.getSystemUserToken();
//
//        if (accessToken == null) {
//            throw new IllegalStateException("System user token missing in DB");
//        }
//
//        String url = "https://graph.facebook.com/v22.0/"
//                + phoneNumberId + "/messages";
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.setBearerAuth(accessToken);
//
//        Map<String, Object> payload = Map.of(
//                "messaging_product", "whatsapp",
//                "to", toNumber,
//                "type", "template",
//                "template", Map.of(
//                        "name", "hello_world",
//                        "language", Map.of("code", "en_US")
//                )
//        );
//
//        ResponseEntity<Map> response =
//                restTemplate.postForEntity(
//                        url,
//                        new HttpEntity<>(payload, headers),
//                        Map.class
//                );
//
//        log.info(
//                "App Review test message sent. phoneNumberId={}, to={}",
//                phoneNumberId,
//                toNumber
//        );
//
//        return Map.of(
//                "phoneNumberId", phoneNumberId,
//                "to", toNumber,
//                "metaResponse", response.getBody()
//        );
//    }
//}


