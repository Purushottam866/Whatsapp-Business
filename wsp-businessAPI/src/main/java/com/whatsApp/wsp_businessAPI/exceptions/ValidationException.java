package com.whatsApp.wsp_businessAPI.exceptions;

public class ValidationException extends ApiException {
    public ValidationException(String message, String errorDetails) {
        super(message, "VALIDATION_ERROR", 400);
    }
}
