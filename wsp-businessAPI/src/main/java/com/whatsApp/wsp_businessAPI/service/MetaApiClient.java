package com.whatsApp.wsp_businessAPI.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.whatsApp.wsp_businessAPI.dto.ObaApplicationRequest;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.exceptions.WhatsAppApiException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@Slf4j
public class MetaApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Define constants for API versions
    private static final String GRAPH_API_BASE = "https://graph.facebook.com";
    private static final String API_VERSION = "v25.0"; // Latest stable version

    @Value("${meta.app.id}")
    private String appId;

    @Value("${meta.app.secret}")
    private String appSecret;

    public Map<String, Object> exchangeAuthorizationCode(String code) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/oauth/access_token"
                + "?client_id=" + appId
                + "&client_secret=" + appSecret
                + "&code=" + code;

        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getBody();
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_TOKEN_EXCHANGE_FAILED");
        }
    }

    public Map<String, Object> debugToken(String token) {

        String appAccess = appId + "|" + appSecret;

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/debug_token"
                + "?input_token=" + token
                + "&access_token=" + appAccess;

        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            return resp.getBody();
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_TOKEN_DEBUG_FAILED");
        }
    }

    public void subscribeWabaToWebhooks(String wabaId, String accessToken) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/"
                + wabaId + "/subscribed_apps";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(headers), Map.class);
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_WEBHOOK_SUBSCRIBE_FAILED");
        }
    }

    public void registerPhoneNumber(String phoneNumberId, String accessToken, String pin) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/"
                + phoneNumberId + "/register";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "pin", pin
        );

        try {
            restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            log.info("✅ Phone number {} registered successfully", phoneNumberId);
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_PHONE_REGISTER_FAILED");
        }
    }

    public String getPhoneRegistrationStatus(String phoneNumberId, String accessToken) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/"
                + phoneNumberId + "?fields=code_verification_status";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return (String) response.getBody().get("code_verification_status");
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_PHONE_STATUS_FAILED");
        }
    }

    public Map<String, Object> fetchPhoneNumberDetails(String phoneNumberId, String accessToken) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/"
                + phoneNumberId + "?fields=display_phone_number,verified_name,quality_rating";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            log.info("📱 Phone metadata fetched: {}", response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_PHONE_DETAILS_FAILED");
        }
    }

    public Map<String, Object> fetchWhatsAppBusinessProfile(String phoneNumberId, String accessToken) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/"
                + phoneNumberId + "/whatsapp_business_profile"
                + "?fields=about,description,email,websites,address";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_PROFILE_FETCH_FAILED");
        }
    }

    private WhatsAppApiException buildMetaException(HttpClientErrorException ex, String errorCode) {
        String metaMessage = ex.getResponseBodyAsString();
        log.error("Meta API error [{}]: {}", errorCode, metaMessage);
        return new WhatsAppApiException(metaMessage, errorCode, ex);
    }

    public Map<String, Object> submitTemplate(
            String wabaId,
            String accessToken,
            String name,
            String category,
            String language,
            String componentsJson) {

        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + wabaId + "/message_templates";

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("name", name);
            payload.put("category", category);
            payload.put("language", language);
            payload.set("components", mapper.readTree(componentsJson));
            payload.put("allow_category_change", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(mapper.writeValueAsString(payload), headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            log.info("Meta template submission response: {}", response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.error("Meta rejected template: {}", errorBody);
            throw new WhatsAppApiException("Template submission failed: " + errorBody, "META_TEMPLATE_SUBMIT_FAILED", ex);
        } catch (Exception e) {
            throw new ValidationException("Failed to build Meta payload", e.getMessage());
        }
    }
    
    /**
     * UPLOAD FOR MESSAGE SENDING - returns REAL media ID
     */
    public String uploadMediaBytes(String phoneNumberId, String token, byte[] imageBytes, String contentType) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + phoneNumberId + "/media";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "template-image.jpg";
            }
        });
        
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            String mediaId = responseBody.get("id").toString();
            log.info("✅✅ REAL MEDIA ID from /media upload: {} (length: {})", mediaId, mediaId.length());
            return mediaId;
        } catch (HttpClientErrorException ex) {
            log.error("Media upload failed: {}", ex.getResponseBodyAsString());
            throw new WhatsAppApiException("Media upload failed: " + ex.getResponseBodyAsString(), "META_MEDIA_UPLOAD_FAILED", ex);
        }
    }
    
    /**
     * UPLOAD FOR TEMPLATE CREATION - returns header_handle
     */
    public Map<String, String> uploadMediaForTemplate(String phoneNumberId, String token, MultipartFile file) {
        try {
            log.info("========== STARTING MEDIA UPLOAD FOR TEMPLATE ==========");
            log.info("PhoneNumberId: {}", phoneNumberId);
            log.info("File name: {}", file.getOriginalFilename());
            log.info("File size: {} bytes", file.getSize());
            log.info("File type: {}", file.getContentType());
            
            String sessionId = createUploadSession(token, file);
            log.info("✅ Upload session created: {}", sessionId);
            
            String headerHandle = uploadFileToSession(sessionId, token, file);
            log.info("✅ Header handle received");
            
            String extractedId = extractPossibleMediaId(headerHandle);
            
            log.info("========== HEADER HANDLE ANALYSIS ==========");
            log.info("📎 Full header_handle: {}", headerHandle);
            log.info("🔍 Extracted ID: {}", extractedId);
            
            if (extractedId != null && extractedId.length() >= 15) {
                log.info("✅✅ VALID MEDIA ID FOUND: {} (length: {})", extractedId, extractedId.length());
            } else if (extractedId != null) {
                log.warn("⚠️ Extracted ID is only {} digits - this is a timestamp/session ID", extractedId.length());
            }
            
            Map<String, String> result = new HashMap<>();
            result.put("extractedId", extractedId);
            result.put("headerHandle", headerHandle);
            result.put("sessionId", sessionId);
            
            log.info("========== MEDIA UPLOAD COMPLETE ==========");
            log.info("Header Handle: {}", headerHandle);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Resumable upload failed", e);
            throw new RuntimeException("Failed to upload media for template: " + e.getMessage());
        }
    }

    private String createUploadSession(String token, MultipartFile file) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/app/uploads";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> body = new HashMap<>();
        body.put("file_length", file.getSize());
        body.put("file_type", file.getContentType());
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            log.info("Upload session response: {}", responseBody);
            return responseBody.get("id").toString();
        } catch (HttpClientErrorException ex) {
            log.error("Failed to create upload session: {}", ex.getResponseBodyAsString());
            throw new WhatsAppApiException("Failed to create upload session: " + ex.getResponseBodyAsString(), "META_UPLOAD_SESSION_FAILED", ex);
        }
    }

    private String uploadFileToSession(String sessionId, String token, MultipartFile file) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + sessionId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        
        HttpEntity<byte[]> request;
        try {
            request = new HttpEntity<>(file.getBytes(), headers);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file");
        }
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            String responseBody = response.getBody();
            log.info("File upload response: {}", responseBody);
            
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            if (jsonResponse.has("h")) {
                return jsonResponse.get("h").asText();
            } else {
                throw new RuntimeException("No 'h' field in response: " + responseBody);
            }
        } catch (HttpClientErrorException ex) {
            log.error("Failed to upload file to session: {}", ex.getResponseBodyAsString());
            throw new WhatsAppApiException("Failed to upload file to session: " + ex.getResponseBodyAsString(), "META_FILE_UPLOAD_FAILED", ex);
        } catch (Exception e) {
            log.error("Failed to parse upload response", e);
            throw new RuntimeException("Failed to parse upload response: " + e.getMessage());
        }
    }

    private String extractPossibleMediaId(String headerHandle) {
        if (headerHandle == null) return null;
        try {
            String[] parts = headerHandle.split(":");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("e".equals(parts[i]) && i + 1 < parts.length) {
                    String candidate = parts[i + 1];
                    if (candidate.matches("\\d+")) {
                        return candidate;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract ID from header_handle", e);
        }
        return null;
    }

    public String uploadMedia(String phoneNumberId, String token, MultipartFile file) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + phoneNumberId + "/media";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("type", file.getContentType());
        
        try {
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file");
        }
        
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            log.info("Media upload response: {}", responseBody);
            return responseBody.get("id").toString();
        } catch (HttpClientErrorException ex) {
            log.error("Media upload failed: {}", ex.getResponseBodyAsString());
            throw new WhatsAppApiException("Media upload failed: " + ex.getResponseBodyAsString(), "META_MEDIA_UPLOAD_FAILED", ex);
        }
    }

    public Map<String, Object> getTemplateStatus(String templateId, String accessToken) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + templateId + "?fields=status";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw buildMetaException(ex, "META_TEMPLATE_STATUS_FETCH_FAILED");
        }
    }

    /**
     * ✅ UPDATED: Send template message with proper error handling
     */
    public Map<String, Object> sendTemplateMessage(
            String phoneNumberId,
            String accessToken,
            String to,
            String templateName,
            String language,
            Map<String, Object> parameters
    ) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + phoneNumberId + "/messages";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", language));
        
        if (parameters != null && parameters.containsKey("components")) {
            template.put("components", parameters.get("components"));
            log.info("Using provided components structure");
        }
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "template");
        payload.put("template", template);
        
        log.info("📤 Sending payload to Meta (v{})", API_VERSION);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload, headers), Map.class);
            
            Map<String, Object> responseBody = response.getBody();
            log.info("✅ Meta send response: {}", responseBody);
            
            // Check if response contains error (shouldn't happen for 200, but just in case)
            if (responseBody.containsKey("error")) {
                log.error("❌ Meta returned error in response body: {}", responseBody.get("error"));
            }
            
            return responseBody;
            
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.error("❌ Meta message send failed with status {}: {}", 
                      ex.getStatusCode(), errorBody);
            
            // Parse the error response
            try {
                Map<String, Object> errorResponse = objectMapper.readValue(errorBody, Map.class);
                return errorResponse; // Return the error response to the service layer
            } catch (Exception e) {
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("error", Map.of(
                    "message", errorBody,
                    "code", ex.getStatusCode().value()
                ));
                return fallback;
            }
        }
    }
    
    
    /**
     * Send regular text message (non-template)
     * This is for chat replies, not for template messages
     */
    public Map<String, Object> sendTextMessage(
            String phoneNumberId,
            String accessToken,
            String to,
            String text) {
        
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + phoneNumberId + "/messages";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", to);
        payload.put("type", "text");
        
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("body", text);
        payload.put("text", textObj);
        
        log.info("📤 Sending text message to: {}", to);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(payload, headers), Map.class);
            
            Map<String, Object> responseBody = response.getBody();
            log.info("✅ Text message sent successfully. Response: {}", responseBody);
            
            return responseBody;
            
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.error("❌ Text message failed: {}", errorBody);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", errorBody);
            errorResponse.put("statusCode", ex.getStatusCode().value());
            return errorResponse;
        }
    }
    
    
//    /**
//     * Get media info from Meta
//     */
//    public Map<String, Object> getMediaInfo(String mediaId, String token) {
//        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + mediaId;
//        
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(token);
//        
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//        
//        try {
//            ResponseEntity<Map> response = restTemplate.exchange(
//                url, HttpMethod.GET, request, Map.class);
//            return response.getBody();
//        } catch (HttpClientErrorException ex) {
//            log.error("Failed to get media info: {}", ex.getResponseBodyAsString());
//            Map<String, Object> error = new HashMap<>();
//            error.put("error", ex.getMessage());
//            return error;
//        }
//    }
    

    public JsonNode fetchTemplates(String wabaId, String accessToken) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode allTemplates = mapper.createArrayNode();
        
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + wabaId +
                "/message_templates?fields=name,status,language,category,quality_score";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        try {
            int safetyCounter = 0;
            while (url != null && safetyCounter < 20) {
                safetyCounter++;
                HttpEntity<Void> request = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
                JsonNode root = mapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode node : data) {
                        allTemplates.add(node);
                    }
                }
                JsonNode paging = root.get("paging");
                url = (paging != null && paging.has("next")) ? paging.get("next").asText() : null;
            }
            ObjectNode result = mapper.createObjectNode();
            result.set("data", allTemplates);
            return result;
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.error("Meta template fetch failed: {}", errorBody);
            throw new WhatsAppApiException("Failed to fetch templates: " + errorBody, "META_TEMPLATE_FETCH_FAILED", ex);
        } catch (Exception e) {
            throw new WhatsAppApiException("Unexpected error while fetching templates", "META_TEMPLATE_FETCH_FAILED", e);
        }
    }

    public Map<String, Object> submitObaApplication(String phoneNumberId, String token, ObaApplicationRequest request) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + phoneNumberId + "/official_business_account";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> body = new HashMap<>();
        body.put("business_website_url", request.getBusinessWebsiteUrl());
        body.put("primary_country_of_operation", request.getPrimaryCountryOfOperation());
        body.put("primary_language", request.getPrimaryLanguage());
        body.put("supporting_links", request.getSupportingLinks());
        if (request.getAdditionalSupportingInformation() != null) {
            body.put("additional_supporting_information", request.getAdditionalSupportingInformation());
        }
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            log.error("OBA apply failed: {}", ex.getResponseBodyAsString());
            throw new WhatsAppApiException("Blue tick application failed: " + ex.getResponseBodyAsString(), "META_OBA_FAILED");
        }
    }

    public Map<String, Object> getObaStatus(String phoneNumberId, String token) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + phoneNumberId + "?fields=official_business_account";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        return response.getBody();
    }

    public Map<String, Object> getMediaInfo(String mediaId, String token) {
        String url = GRAPH_API_BASE + "/" + API_VERSION + "/" + mediaId + "?fields=id,url,mime_type,file_size";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            log.error("Failed to get media info: {}", ex.getResponseBodyAsString());
            throw new WhatsAppApiException("Failed to get media info: " + ex.getResponseBodyAsString(), "META_MEDIA_INFO_FAILED", ex);
        }
    }
}