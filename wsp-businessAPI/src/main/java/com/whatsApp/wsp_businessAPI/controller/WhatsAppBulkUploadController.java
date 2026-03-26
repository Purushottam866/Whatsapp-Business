package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.whatsApp.wsp_businessAPI.dto.BulkSendWhatsAppMessageRequest;
import com.whatsApp.wsp_businessAPI.service.FileParserService;
import com.whatsApp.wsp_businessAPI.service.WhatsAppMessageService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp/messages")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppBulkUploadController {

    private final FileParserService fileParserService;
    private final WhatsAppMessageService messageService;

    @PostMapping(value = "/upload-bulk", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<?>> uploadBulk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("wabaId") @NotBlank(message = "WABA ID is required") String wabaId,
            @RequestParam("phoneNumberId") @NotBlank(message = "Phone Number ID is required") String phoneNumberId,
            @RequestParam("templateName") @NotBlank(message = "Template name is required") String templateName,
            @RequestParam("language") @NotBlank(message = "Language code is required") 
            @Pattern(regexp = "^[a-z]{2}(_[A-Z]{2})?$", message = "Language must be format 'en' or 'en_US'") String language) {

        log.info("========== BULK UPLOAD REQUEST ==========");
        log.info("File: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());
        log.info("WABA ID: {}, Phone Number ID: {}, Template: {}, Language: {}", 
                 wabaId, phoneNumberId, templateName, language);

        try {
            // Step 1: Validate and parse file
            long startTime = System.currentTimeMillis();
            List<String> phoneNumbers = fileParserService.extractPhoneNumbers(file);
            long parseTime = System.currentTimeMillis() - startTime;
            
            log.info("✅ File parsed in {} ms - Found {} valid phone numbers", parseTime, phoneNumbers.size());

            // Step 2: Validate minimum numbers
            if (phoneNumbers.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, 
                        "No valid phone numbers found", 
                        "NO_VALID_NUMBERS",
                        "The file contains no valid phone numbers. Please check the format."));
            }

            if (phoneNumbers.size() > 10000) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, 
                        "Too many numbers", 
                        "EXCEEDS_LIMIT",
                        "Maximum 10,000 numbers allowed per batch. Found: " + phoneNumbers.size()));
            }

            // Step 3: Create bulk request with phoneNumberId
            BulkSendWhatsAppMessageRequest bulkRequest = new BulkSendWhatsAppMessageRequest();
            bulkRequest.setWabaId(wabaId);
            bulkRequest.setPhoneNumberId(phoneNumberId); // Now required
            bulkRequest.setTemplateName(templateName);
            bulkRequest.setLanguage(language);
            bulkRequest.setRecipients(phoneNumbers);

            // Step 4: Send to existing service
            ApiResponse<?> serviceResponse = messageService.sendBulk(bulkRequest);

            // Step 5: Enhance response with file info
            Map<String, Object> enhancedData = new HashMap<>();
            
            if (serviceResponse.getData() instanceof Map) {
                Map<?, ?> responseData = (Map<?, ?>) serviceResponse.getData();
                for (Map.Entry<?, ?> entry : responseData.entrySet()) {
                    if (entry.getKey() instanceof String) {
                        enhancedData.put((String) entry.getKey(), entry.getValue());
                    }
                }
            }
            
            enhancedData.put("fileProcessed", file.getOriginalFilename());
            enhancedData.put("totalNumbersFound", phoneNumbers.size());
            enhancedData.put("phoneNumberId", phoneNumberId);
            enhancedData.put("processingTimeMs", parseTime);

            log.info("✅ Bulk upload completed - Campaign created: {} with Phone Number ID: {}", 
                enhancedData.get("campaignId"), phoneNumberId);
            log.info("========== BULK UPLOAD COMPLETE ==========");

            return ResponseEntity.ok(
                ApiResponse.success(
                    "File processed successfully. Campaign created.",
                    enhancedData
                )
            );

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, 
                    "Invalid request", 
                    "VALIDATION_ERROR",
                    e.getMessage()));
        } catch (Exception e) {
            log.error("Bulk upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error(500, 
                    "Failed to process bulk upload", 
                    "BULK_UPLOAD_FAILED",
                    e.getMessage()));
        }
    }

    /**
     * Get available phone numbers for a WABA
     */
    @GetMapping("/available-numbers")
    public ResponseEntity<ApiResponse<?>> getAvailablePhoneNumbers(
            @RequestParam("wabaId") String wabaId) {
        
        // You can add a service method to fetch phone numbers
        // For now, returning static list from your DB data
        List<Map<String, String>> phoneNumbers = List.of(
            Map.of("phoneNumberId", "1051619728024789", "displayNumber", "+91 99001 36133"),
            Map.of("phoneNumberId", "1038405262688837", "displayNumber", "+91 9845233071")
        );
        
        return ResponseEntity.ok(
            ApiResponse.success("Available phone numbers", phoneNumbers)
        );
    }

    /**
     * Sample template endpoint
     */
    @GetMapping("/upload-bulk/sample")
    public ResponseEntity<ApiResponse<?>> getSampleTemplate() {
        
        Map<String, Object> sample = new HashMap<>();
        sample.put("fileFormat", "Excel (.xlsx) or CSV");
        sample.put("expectedColumnHeaders", List.of(
            "phone", "mobile", "contact", "number", "phone number"
        ));
        sample.put("example", List.of(
            "9876543210", "919876543210", "+91 98765 43210", "99887 76655"
        ));
        sample.put("maxFileSize", "10MB");
        sample.put("maxNumbersPerBatch", 10000);
        sample.put("requiredParams", List.of(
            "wabaId", "phoneNumberId", "templateName", "language"
        ));
        sample.put("notes", List.of(
            "Spaces and +91 prefix are automatically handled",
            "Invalid numbers are skipped and logged",
            "First non-empty value from any phone column is used",
            "Header row is auto-detected within first 10 rows"
        ));

        return ResponseEntity.ok(
            ApiResponse.success("Sample template info", sample)
        );
    }
}