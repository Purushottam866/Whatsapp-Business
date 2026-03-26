package com.whatsApp.wsp_businessAPI.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    // email OR phone number
    private String identifier;

    private String password;
}
