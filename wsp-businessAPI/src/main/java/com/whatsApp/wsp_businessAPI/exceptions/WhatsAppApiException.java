package com.whatsApp.wsp_businessAPI.exceptions;

import lombok.Data;

@Data
public class WhatsAppApiException extends ApiException {
    public WhatsAppApiException(String message, String errorCode) {
        super(message, errorCode, 502); // 502 Bad Gateway for external API failures
    }
    
    public WhatsAppApiException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, 502, cause);
    }
}

