package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.dto.AuthResponse;
import com.whatsApp.wsp_businessAPI.dto.ForgotPasswordRequest;
import com.whatsApp.wsp_businessAPI.dto.LoginRequest;
import com.whatsApp.wsp_businessAPI.dto.RegisterRequest;
import com.whatsApp.wsp_businessAPI.dto.ResendOtpRequest;
import com.whatsApp.wsp_businessAPI.dto.ResetPasswordRequest;
import com.whatsApp.wsp_businessAPI.dto.VerifyEmailRequest;
import com.whatsApp.wsp_businessAPI.service.AuthService;
import com.whatsApp.wsp_businessAPI.service.CurrentTenantService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentTenantService currentTenantService;

    // REGISTER
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<?>> register(@RequestBody RegisterRequest request) {
        try {
            authService.register(request);
            return ResponseEntity.ok(ApiResponse.success("OTP sent to email", request.getEmail()));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(
                    ApiResponse.error(
                            400,
                            "Registration failed",
                            "REGISTRATION_FAILED",
                            e.getMessage()
                    )
            );
        }
    }

    // VERIFY EMAIL
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<?>> verifyEmail(@RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }

    // RESEND OTP
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<?>> resendOtp(@RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP resent successfully", null));
    }

    // LOGIN (EMAIL OR PHONE)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", token));
    }

    // LOGOUT
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest request) {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            authService.logout(token);
        }

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    // FORGOT PASSWORD
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<?>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Reset OTP sent to email", null));
    }

    // RESET PASSWORD
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<?>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful", null));
    }
    
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> getCurrentUser() {
         
        Long userId = currentTenantService.getUserId();
        Long tenantId = currentTenantService.getTenantId();
        
        ApiResponse<?> response = authService.getCurrentUser(userId, tenantId);
        
        return ResponseEntity.ok(response);
    }
}
