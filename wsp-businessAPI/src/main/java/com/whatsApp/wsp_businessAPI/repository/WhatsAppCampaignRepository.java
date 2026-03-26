package com.whatsApp.wsp_businessAPI.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppCampaign;

@Repository
public interface WhatsAppCampaignRepository extends JpaRepository<WhatsAppCampaign, Long> {

    // Find campaigns by template name
    List<WhatsAppCampaign> findByTenantIdAndTemplateName(Long tenantId, String templateName);

    // Find campaigns by template name and WABA
    List<WhatsAppCampaign> findByTenantIdAndTemplateNameAndWabaId(
        Long tenantId, String templateName, String wabaId);

    // REMOVE this one - no phoneNumberId field
    // List<WhatsAppCampaign> findByTenantIdAndTemplateNameAndWabaIdAndPhoneNumberId(...);

    // Get distinct template names
    @Query("SELECT DISTINCT c.templateName FROM WhatsAppCampaign c WHERE c.tenantId = :tenantId")
    List<String> findDistinctTemplateNamesByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT DISTINCT c.templateName FROM WhatsAppCampaign c WHERE c.tenantId = :tenantId AND c.wabaId = :wabaId")
    List<String> findDistinctTemplateNamesByTenantIdAndWabaId(
        @Param("tenantId") Long tenantId, @Param("wabaId") String wabaId);
        
    // Check for duplicate campaigns
    boolean existsByTenantIdAndWabaIdAndTemplateNameAndStatusIn(
        Long tenantId, String wabaId, String templateName, List<WhatsAppCampaign.Status> statuses);
}