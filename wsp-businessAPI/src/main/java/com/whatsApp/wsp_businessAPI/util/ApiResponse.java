package com.whatsApp.wsp_businessAPI.util;




 
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private int statusCode;
    private String message;
    private T data;
    private String errorCode;
    private String errorDetails;
    private String path;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // ========== SUCCESS FACTORY METHODS ==========
    
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .statusCode(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .statusCode(201)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // ========== ERROR FACTORY METHODS (FIXED) ==========
    
    // Generic error method with type parameter
    public static <T> ApiResponse<T> error(int statusCode, String message, String errorCode, String errorDetails) {
        return ApiResponse.<T>builder()
                .statusCode(statusCode)
                .message(message)
                .errorCode(errorCode)
                .errorDetails(errorDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // Typed error methods
    public static ApiResponse<String> badRequest(String message, String errorDetails) {
        return error(400, message, "BAD_REQUEST", errorDetails);
    }
    
    public static ApiResponse<String> unauthorized(String message) {
        return error(401, message, "UNAUTHORIZED", "Authentication required");
    }
    
    public static ApiResponse<String> forbidden(String message) {
        return error(403, message, "FORBIDDEN", "Insufficient permissions");
    }
    
    public static ApiResponse<String> notFound(String message) {
        return error(404, message, "NOT_FOUND", "Resource not found");
    }
    
    public static ApiResponse<String> conflict(String message, String errorDetails) {
        return error(409, message, "CONFLICT", errorDetails);
    }
    
    public static ApiResponse<String> internalError(String message, String errorDetails) {
        return error(500, message, "INTERNAL_ERROR", errorDetails);
    }
    
    public static ApiResponse<String> serviceUnavailable(String message, String errorDetails) {
        return error(503, message, "SERVICE_UNAVAILABLE", errorDetails);
    }
}
