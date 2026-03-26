package com.whatsApp.wsp_businessAPI.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String fullName;
    
    @Column(nullable = false, unique = true, length = 120)
    private String email;
    
    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;
    
    private LocalDateTime otpExpiry;
    
    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "verification_otp")
    private String verificationOtp;

    @Column(nullable = false)
    private String password;

    @Column(name = "reset_otp", length = 10)
    private String resetOtp;

    @Column(name = "reset_requested")
    private Boolean resetRequested;
    
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "is_active")
    private Boolean isActive;

    public enum Role {
        OWNER,
        ADMIN,
        MEMBER
    }

    @PrePersist
    public void prePersist() {
        if (isActive == null) isActive = true;
        if (emailVerified == null) emailVerified = false;
        if (resetRequested == null) resetRequested = false;
        createdAt = LocalDateTime.now();
    }
}
