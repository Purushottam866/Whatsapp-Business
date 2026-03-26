package com.whatsApp.wsp_businessAPI.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendWhatsAppMessageRequest {

    @NotBlank(message = "wabaId is required")
    private String wabaId;

    // ✅ NEW: Allow frontend to specify which phone number to send from
    private String phoneNumberId;  // Optional - if not provided, system will use default

    @NotBlank(message = "templateName is required")
    private String templateName;

    @NotBlank(message = "language is required")
    private String language;

    private Map<String, Object> parameters;

    @NotBlank(message = "Recipient phone number is required")
    @Pattern(
            regexp = "^[1-9][0-9]{7,14}$",
            message = "Phone number must be in international format WITHOUT +. Example: 919876543210"
    )
    private String to;
}