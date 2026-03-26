package com.whatsApp.wsp_businessAPI.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate.Status;

@Repository
public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, Long> {

	Optional<WhatsAppTemplate> findByMetaTemplateId(String metaTemplateId);
	
	List<WhatsAppTemplate> findByStatusIn(List<WhatsAppTemplate.Status> statuses);
	
    Optional<WhatsAppTemplate> findByWabaIdAndNameAndLanguage(
            String wabaId,
            String name,
            String language
    );

    // ADD THIS
    Optional<WhatsAppTemplate> findByWabaIdAndName(
            String wabaId,
            String name
    );

    List<WhatsAppTemplate> findByTenantIdAndWabaId(
            Long tenantId,
            String wabaId
    );

    List<WhatsAppTemplate> findByStatus(WhatsAppTemplate.Status status);
}