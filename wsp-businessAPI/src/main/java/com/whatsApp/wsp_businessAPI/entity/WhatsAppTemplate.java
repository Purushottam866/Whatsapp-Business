package com.whatsApp.wsp_businessAPI.entity;

import java.time.LocalDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "whatsapp_templates",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_waba_template_name_lang",
            columnNames = {"waba_id", "name", "language"}
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppTemplate {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "waba_id", nullable = false, length = 30)
    private String wabaId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "meta_template_id", length = 100)
    private String metaTemplateId;
    
    @Column(name = "has_parameters")
    private Boolean hasParameters;
    
    @Column(name = "last_status_check_at")
    private LocalDateTime lastStatusCheckAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    // ✅ HEADER HANDLE - for template preview only
    @Column(name = "header_handle", columnDefinition = "TEXT")
    private String headerHandle;

    // ✅ REAL MEDIA ID - for sending messages (15-20 digits)
    @Column(name = "header_media_id", length = 255)
    private String headerMediaId;

    @Lob
    @Column(name = "components_json", columnDefinition = "LONGTEXT")
    private String componentsJson;

    // ✅ Backup image storage (optional but recommended)
    @OneToOne(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private TemplateImage templateImage;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Category {
        MARKETING,
        UTILITY,
        AUTHENTICATION
    }

    public enum Status {
        DRAFT,
        SUBMITTED,
        APPROVED,
        REJECTED,
        PAUSED,
        DISABLED, 
        PENDING, 
        IN_APPEAL, 
        FLAGGED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = Status.DRAFT;
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean hasImageHeader() {
        if (componentsJson == null) return false;
        return componentsJson.contains("\"HEADER\"") && 
               componentsJson.contains("\"IMAGE\"");
    }
}