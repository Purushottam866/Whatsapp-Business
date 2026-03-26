package com.whatsApp.wsp_businessAPI.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "whatsapp_messages",
    indexes = {
        @Index(name = "idx_msg_tenant", columnList = "tenant_id"),
        @Index(name = "idx_msg_waba", columnList = "waba_id"),
        @Index(name = "idx_msg_phone", columnList = "phone_number"),
        @Index(name = "idx_msg_status", columnList = "status"),
        @Index(name = "idx_msg_meta_id", columnList = "meta_message_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "waba_id", nullable = false)
    private String wabaId;

    @Column(name = "phone_number_id", nullable = false)
    private String phoneNumberId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "template_language", nullable = false)
    private String templateLanguage;

    @Column(name = "template_category", nullable = false)
    private String templateCategory;

    @Column(name = "template_parameters", columnDefinition = "TEXT")
    private String templateParametersJson;

    @Column(name = "meta_message_id", length = 150)
    private String metaMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at") 
    private LocalDateTime readAt;

    @Column(name = "customer_replied", nullable = false)
    private Boolean customerReplied;
    
    @Column(name = "customer_reply_text", length = 1000)
    private String customerReplyText;
    
    @Column(name = "customer_reply_type", length = 20)
    private String customerReplyType; // "text", "image", "button", etc.
    
    @Column(name = "customer_replied_at")
    private LocalDateTime customerRepliedAt;
    
    @Column(name = "campaign_id")
    private Long campaignId;
    
    @Column(name = "failure_count")
    private Integer failureCount;
    
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    public enum Status {
        QUEUED,
        PENDING,     // ✅ ADD THIS - Message accepted by Meta but not yet delivered
        SENT,
        DELIVERED,
        READ,
        FAILED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = Status.QUEUED;
        if (customerReplied == null) customerReplied = false;
        if (failureCount == null) failureCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}