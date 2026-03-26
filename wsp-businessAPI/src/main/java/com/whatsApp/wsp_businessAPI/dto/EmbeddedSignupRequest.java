package com.whatsApp.wsp_businessAPI.dto;

import java.util.Map;

import lombok.Data;

@Data
public class EmbeddedSignupRequest {
    private String authorizationCode;
    private String wabaId;
    private String phoneNumberId;
    private String businessId;

    private Map<String,Object> rawPayload; // add this
}
 

