package com.whatsApp.wsp_businessAPI.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BulkSendWhatsAppMessageRequest {

    @NotBlank
    private String wabaId;

    // ✅ NEW: Allow frontend to specify which phone number to send from
    private String phoneNumberId;  // Optional - if not provided, system will use default

    @NotBlank
    private String templateName;

    @NotBlank
    private String language;

    @NotEmpty(message = "Recipients list cannot be empty")
    private List<@Pattern(
            regexp = "^[1-9][0-9]{7,14}$",
            message = "Invalid phone number format. Use 919876543210"
    ) String> recipients;

    private Map<String, Object> parameters;
}