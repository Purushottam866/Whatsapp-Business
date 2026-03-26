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
@Table(name = "whatsapp_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "waba_id", nullable = false)
    private String wabaId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Column(name = "template_language", nullable = false)
    private String templateLanguage;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "total_contacts")
    private Integer totalContacts;

    @Column(name = "processed_contacts")
    private Integer processedContacts;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum Status {
        CREATED,
        RUNNING,
        COMPLETED,
        PAUSED
    }

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = Status.CREATED;
        if (processedContacts == null) processedContacts = 0;
    }
}