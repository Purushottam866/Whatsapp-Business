package com.whatsApp.wsp_businessAPI.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.dto.AuthResponse;
import com.whatsApp.wsp_businessAPI.dto.ForgotPasswordRequest;
import com.whatsApp.wsp_businessAPI.dto.LoginRequest;
import com.whatsApp.wsp_businessAPI.dto.RegisterRequest;
import com.whatsApp.wsp_businessAPI.dto.ResendOtpRequest;
import com.whatsApp.wsp_businessAPI.dto.ResetPasswordRequest;
import com.whatsApp.wsp_businessAPI.dto.VerifyEmailRequest;
import com.whatsApp.wsp_businessAPI.entity.RevokedToken;
import com.whatsApp.wsp_businessAPI.entity.User;
import com.whatsApp.wsp_businessAPI.repository.RevokedTokenRepository;
import com.whatsApp.wsp_businessAPI.repository.UserRepository;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final JavaMailSender mailSender;

    // OTP
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("quantumshare18@gmail.com");
            message.setTo(email);
            message.setSubject("Quantum Share - Verification OTP");
            message.setText("Your OTP is: " + otp + "\nValid for 15 minutes.");

            mailSender.send(message);
        } catch (MailException e) {
            throw new RuntimeException("Email service unavailable. Please try again later.");
        }
    }

    // REGISTER
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("Email already registered");

        if (request.getPhoneNumber() != null &&
                userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new RuntimeException("Phone number already registered");

        if (!request.getPassword().equals(request.getConfirmPassword()))
            throw new RuntimeException("Passwords do not match");

        Long tenantId = userRepository.findMaxTenantId()
                .map(id -> id + 1)
                .orElse(101L);

        String otp = generateOtp();

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .tenantId(tenantId)
                .role(User.Role.OWNER)
                .emailVerified(false)
                .verificationOtp(otp)
                .otpExpiry(LocalDateTime.now().plusMinutes(15))
                .build();

        userRepository.save(user);
        sendOtpEmail(user.getEmail(), otp);
    }

    // VERIFY EMAIL
    public void verifyEmail(VerifyEmailRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getOtpExpiry().isBefore(LocalDateTime.now()))
            throw new RuntimeException("OTP expired");

        if (!user.getVerificationOtp().equals(request.getOtp()))
            throw new RuntimeException("Invalid OTP");

        user.setEmailVerified(true);
        user.setVerificationOtp(null);
        user.setOtpExpiry(null);

        userRepository.save(user);
    }

    // RESEND OTP
    public void resendOtp(ResendOtpRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String otp = generateOtp();
        user.setVerificationOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);
        sendOtpEmail(user.getEmail(), otp);
    }

    // LOGIN (PHONE OR EMAIL)
    public String login(LoginRequest request) {

        User user;

        if (request.getIdentifier().matches("\\d{10,15}")) {
            user = userRepository.findByPhoneNumber(request.getIdentifier())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        } else {
            user = userRepository.findByEmail(request.getIdentifier())
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified()))
            throw new RuntimeException("Please verify email first");

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new RuntimeException("Invalid credentials");

        return jwtService.generateToken(user);
    }

    // LOGOUT
    public void logout(String token) {
        
        RevokedToken.RevokedTokenBuilder revokedBuilder = RevokedToken.builder()
                .token(token);
        
        try {
            // Try to get expiration if it exists
            Date expiry = jwtService.extractExpiration(token);
            if (expiry != null) {
                revokedBuilder.expiryDate(expiry.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
            } else {
                // For tokens without expiration, set a far future date for cleanup
                revokedBuilder.expiryDate(LocalDateTime.now().plusYears(1));
            }
        } catch (Exception e) {
            // If can't extract expiration, use future date
            revokedBuilder.expiryDate(LocalDateTime.now().plusYears(1));
        }
        
        revokedTokenRepository.save(revokedBuilder.build());
    }

    // FORGOT PASSWORD
    public void forgotPassword(ForgotPasswordRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String otp = generateOtp();

        user.setResetOtp(otp);
        user.setResetRequested(true);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(15));

        userRepository.save(user);
        sendOtpEmail(user.getEmail(), otp);
    }

    // RESET PASSWORD
    public void resetPassword(ResetPasswordRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getOtpExpiry().isBefore(LocalDateTime.now()))
            throw new RuntimeException("OTP expired");

        if (!user.getResetOtp().equals(request.getOtp()))
            throw new RuntimeException("Invalid OTP");

        if (!request.getNewPassword().equals(request.getConfirmPassword()))
            throw new RuntimeException("Passwords do not match");

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetOtp(null);
        user.setResetRequested(false);
        user.setOtpExpiry(null);

        userRepository.save(user);
    }

	public ApiResponse<?> getCurrentUser(Long userId, Long tenantId) {
    
    log.info("Fetching current user details - userId: {}, tenantId: {}", userId, tenantId);
    
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    
    // Verify tenant matches (security check)
    if (!user.getTenantId().equals(tenantId)) {
        throw new RuntimeException("Tenant mismatch - unauthorized");
    }
    
    Map<String, Object> userInfo = new HashMap<>();
    userInfo.put("id", user.getId());
    userInfo.put("fullName", user.getFullName());
    userInfo.put("email", user.getEmail());
    userInfo.put("phoneNumber", user.getPhoneNumber());
    userInfo.put("tenantId", user.getTenantId());
    userInfo.put("role", user.getRole());
    userInfo.put("emailVerified", user.getEmailVerified());
    userInfo.put("isActive", user.getIsActive());
    userInfo.put("createdAt", user.getCreatedAt()); // You may need to add this field
    
    return ApiResponse.success(
            "User details fetched successfully",
            userInfo
    );
}
}