package com.whatsApp.wsp_businessAPI.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WhatsAppTestSendRequest {

    @NotBlank
    private String phoneNumberId; // TEST phone number id

    @NotBlank
    private String to; // recipient number
}
