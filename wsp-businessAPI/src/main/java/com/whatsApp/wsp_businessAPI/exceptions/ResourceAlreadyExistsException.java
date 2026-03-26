package com.whatsApp.wsp_businessAPI.exceptions;

public class ResourceAlreadyExistsException extends ApiException {
    public ResourceAlreadyExistsException(String message) {
        super(message, "RESOURCE_CONFLICT", 409);
    }
}
