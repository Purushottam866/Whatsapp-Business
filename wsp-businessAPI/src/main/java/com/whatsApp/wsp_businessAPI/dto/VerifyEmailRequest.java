package com.whatsApp.wsp_businessAPI.dto;

import lombok.Data;

@Data
public class VerifyEmailRequest {
    private String email;
    private String otp;
}
