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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "whatsapp_accounts")  // Removed unique constraint
public class WhatsAppAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "waba_id", nullable = false)
    private String wabaId;

    @Column(name = "phone_number_id", nullable = false)
    private String phoneNumberId;
    
    @Column(name = "profile_name", length = 150)
    private String profileName;

    @Column(name = "display_phone_number")
    private String displayPhoneNumber;

    @Column(name = "system_user_id")
    private String systemUserId;

    @Column(name = "system_user_token", length = 1000)
    private String systemUserToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "token_last_validated_at")
    private LocalDateTime tokenLastValidatedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "webhook_configured")
    private Boolean webhookConfigured;
    
    @Column(name = "phone_registered")
    private Boolean phoneRegistered;

    @Column(name = "meta_api_version", length = 10)
    private String metaApiVersion;

    @Column(name = "quality_rating", length = 20)
    private String qualityRating;

    @Column(name = "is_test_account")
    private Boolean isTestAccount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,
        ACTIVE,
        SUSPENDED,
        REVOKED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;

        if (status == null) status = Status.PENDING;
        if (isTestAccount == null) isTestAccount = false;
        if (webhookConfigured == null) webhookConfigured = false;
        if (phoneRegistered == null) phoneRegistered = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}