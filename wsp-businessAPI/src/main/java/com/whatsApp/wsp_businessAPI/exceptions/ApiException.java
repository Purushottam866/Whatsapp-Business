package com.whatsApp.wsp_businessAPI.exceptions;

import lombok.Data;

@Data
public class ApiException extends RuntimeException {
    private final String errorCode;
    private final int statusCode;
    
    public ApiException(String message, String errorCode, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }
    
    public ApiException(String message, String errorCode, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }
}
