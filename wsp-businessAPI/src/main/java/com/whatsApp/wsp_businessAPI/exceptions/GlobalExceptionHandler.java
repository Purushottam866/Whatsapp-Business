package com.whatsApp.wsp_businessAPI.exceptions;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<?>> handleApiException(ApiException ex, WebRequest request) {
        log.error("API Exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(
                        ex.getStatusCode(),
                        ex.getMessage(),
                        ex.getErrorCode(),
                        request.getDescription(false)
                ));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String errorDetails = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        log.error("Validation error: {}", errorDetails);
        
        return ResponseEntity.status(400)
                .body(ApiResponse.badRequest("Validation failed", errorDetails));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unhandled exception: ", ex);
        
        return ResponseEntity.status(500)
                .body(ApiResponse.internalError(
                        "An unexpected error occurred",
                        ex.getMessage() != null ? ex.getMessage() : "No details available"
                ));
    }
}
