package com.whatsApp.wsp_businessAPI.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;

import jakarta.transaction.Transactional;

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

    // ===== CUSTOMER REPLIES METHODS =====
    
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrue(Long tenantId);
    
    List<WhatsAppMessage> findByCampaignIdAndCustomerRepliedTrue(Long campaignId);
    
    List<WhatsAppMessage> findByPhoneNumberIdAndCustomerRepliedTrue(String phoneNumberId);
    
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrueAndCustomerRepliedAtBetween(
        Long tenantId, LocalDateTime start, LocalDateTime end);
    
    long countByTenantIdAndCustomerRepliedTrue(Long tenantId);
    
    long countByCampaignIdAndCustomerRepliedTrue(Long campaignId);
    
    @Query("SELECT m FROM WhatsAppMessage m WHERE m.tenantId = :tenantId AND m.customerReplied = true AND LOWER(m.customerReplyText) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<WhatsAppMessage> searchCustomerReplies(
        @Param("tenantId") Long tenantId, 
        @Param("searchText") String searchText);
    
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrueOrderByCustomerRepliedAtDesc(Long tenantId);
    
    List<WhatsAppMessage> findByTenantIdAndCustomerRepliedTrueAndCustomerReplyType(
        Long tenantId, String replyType);

    // ===== CHAT METHODS =====
    
    // Get latest message for a specific sender-recipient conversation
    Optional<WhatsAppMessage> findTopByTenantIdAndPhoneNumberIdAndPhoneNumberOrderByCreatedAtDesc(
        Long tenantId, String phoneNumberId, String recipientPhone);
    
    // Get all messages for a specific sender-recipient conversation
    List<WhatsAppMessage> findByTenantIdAndPhoneNumberIdAndPhoneNumberOrderByCreatedAtAsc(
        Long tenantId, String phoneNumberId, String recipientPhone);
    
    // Get latest message for a specific phone number (for contact details)
    Optional<WhatsAppMessage> findTopByPhoneNumberAndTenantIdOrderByCreatedAtDesc(String phoneNumber, Long tenantId);
    
    // Get all messages between tenant and a specific phone number
    List<WhatsAppMessage> findByTenantIdAndPhoneNumberOrderByCreatedAtAsc(Long tenantId, String phoneNumber);
    
    // Get distinct phone numbers that have replies for this tenant
    @Query("SELECT DISTINCT m.phoneNumber FROM WhatsAppMessage m WHERE m.tenantId = :tenantId AND m.customerReplied = true ORDER BY m.customerRepliedAt DESC")
    List<String> findDistinctPhoneNumbersWithRepliesByTenantId(@Param("tenantId") Long tenantId);
    
    // Update contact name with tenant filter
    @Modifying
    @Transactional
    @Query("UPDATE WhatsAppMessage m SET m.contactName = :contactName WHERE m.tenantId = :tenantId AND m.phoneNumber = :phoneNumber")
    int updateContactNameByPhoneNumberAndTenantId(
        @Param("tenantId") Long tenantId, 
        @Param("phoneNumber") String phoneNumber, 
        @Param("contactName") String contactName);
    
    // ===== OPTIMIZED CHAT METHOD =====
    
 // Add this method to WhatsAppMessageRepository.java

    /**
     * OPTIMIZED: Get all conversations with latest message in a single query
     * Returns: [phoneNumber, contactName, lastMessageTime, lastMessage, isReceived]
     */
    @Query(value = """
        SELECT 
            m.phone_number,
            MAX(m.contact_name) as contact_name,
            MAX(m.created_at) as last_message_time,
            COALESCE(
                (SELECT m2.customer_reply_text 
                 FROM whatsapp_messages m2 
                 WHERE m2.tenant_id = :tenantId 
                   AND m2.phone_number_id = :phoneNumberId 
                   AND m2.phone_number = m.phone_number 
                 ORDER BY m2.created_at DESC LIMIT 1),
                (SELECT m2.template_name 
                 FROM whatsapp_messages m2 
                 WHERE m2.tenant_id = :tenantId 
                   AND m2.phone_number_id = :phoneNumberId 
                   AND m2.phone_number = m.phone_number 
                 ORDER BY m2.created_at DESC LIMIT 1),
                'No messages'
            ) as last_message,
            COALESCE(
                (SELECT m2.customer_replied 
                 FROM whatsapp_messages m2 
                 WHERE m2.tenant_id = :tenantId 
                   AND m2.phone_number_id = :phoneNumberId 
                   AND m2.phone_number = m.phone_number 
                 ORDER BY m2.created_at DESC LIMIT 1), 0
            ) as is_received
        FROM whatsapp_messages m
        WHERE m.tenant_id = :tenantId 
          AND m.phone_number_id = :phoneNumberId
        GROUP BY m.phone_number
        ORDER BY last_message_time DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findConversationsOptimized(
        @Param("tenantId") Long tenantId,
        @Param("phoneNumberId") String phoneNumberId,
        @Param("limit") int limit);
}