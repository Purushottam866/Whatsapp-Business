package com.whatsApp.wsp_businessAPI.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;

@Repository
public interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, Long> {

    // ===== EXISTING METHODS (keep all your existing ones) =====
    
    List<WhatsAppMessage> findAllByTenantId(Long tenantId);
    
    List<WhatsAppMessage> findAllByTenantIdAndStatus(Long tenantId, WhatsAppMessage.Status status);
    
    Optional<WhatsAppMessage> findByMetaMessageId(String metaMessageId);
    
    List<WhatsAppMessage> findAllByTenantIdAndWabaId(Long tenantId, String wabaId);
    
    List<WhatsAppMessage> findAllByTenantIdAndWabaIdAndPhoneNumberId(
        Long tenantId, String wabaId, String phoneNumberId);
    
    List<WhatsAppMessage> findTopByPhoneNumberAndWabaIdOrderByCreatedAtDesc(
        String phoneNumber, String wabaId);
    
    List<WhatsAppMessage> findAllByTenantIdAndWabaIdAndCreatedAtBetween(
        Long tenantId, String wabaId, LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT m FROM WhatsAppMessage m WHERE m.status = :status ORDER BY m.createdAt ASC")
    List<WhatsAppMessage> findTopByStatusOrderByCreatedAtAsc(
        @Param("status") WhatsAppMessage.Status status, Pageable pageable);
    
    default List<WhatsAppMessage> findTopNByStatusOrderByCreatedAtAsc(
            WhatsAppMessage.Status status, int limit) {
        return findTopByStatusOrderByCreatedAtAsc(status, Pageable.ofSize(limit));
    }
    
    long countByCampaignId(Long campaignId);
    
    long countByCampaignIdAndStatus(Long campaignId, WhatsAppMessage.Status status);
    
    List<WhatsAppMessage> findAllByCampaignId(Long campaignId);
    
    List<WhatsAppMessage> findAllByCampaignIdAndTenantId(Long campaignId, Long tenantId);
    
    @Query("SELECT DISTINCT m.phoneNumberId FROM WhatsAppMessage m WHERE m.tenantId = :tenantId AND m.wabaId = :wabaId")
    List<String> findDistinctPhoneNumberIds(@Param("tenantId") Long tenantId, @Param("wabaId") String wabaId);

    // ===== NEW METHODS FOR CUSTOMER REPLIES =====
    
    // Find all messages that received replies for a tenant
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrue(Long tenantId);
    
    // Find replies for a specific campaign
    List<WhatsAppMessage> findByCampaignIdAndCustomerRepliedTrue(Long campaignId);
    
    // Find replies for a specific phone number
    List<WhatsAppMessage> findByPhoneNumberIdAndCustomerRepliedTrue(String phoneNumberId);
    
    // Find replies within a date range
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrueAndCustomerRepliedAtBetween(
        Long tenantId, LocalDateTime start, LocalDateTime end);
    
    // Count total replies for a tenant
    long countByTenantIdAndCustomerRepliedTrue(Long tenantId);
    
    // Count replies for a campaign
    long countByCampaignIdAndCustomerRepliedTrue(Long campaignId);
    
    // Find replies with specific text (search)
    @Query("SELECT m FROM WhatsAppMessage m WHERE m.tenantId = :tenantId AND m.customerReplied = true AND LOWER(m.customerReplyText) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<WhatsAppMessage> searchCustomerReplies(
        @Param("tenantId") Long tenantId, 
        @Param("searchText") String searchText);
    
    // Get latest replies first
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrueOrderByCustomerRepliedAtDesc(Long tenantId);
    
    // Get replies by type (text, image, etc.)
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrueAndCustomerReplyType(
        Long tenantId, String replyType);
}