package com.whatsApp.wsp_businessAPI.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppAccount;

@Repository
public interface WhatsAppAccountRepository extends JpaRepository<WhatsAppAccount, Long> {

    Optional<WhatsAppAccount> findByTenantIdAndWabaId(Long tenantId, String wabaId);
    
    List<WhatsAppAccount> findAllByTenantIdAndWabaId(Long tenantId, String wabaId);
    
    Optional<WhatsAppAccount> findByTenantIdAndWabaIdAndPhoneNumberId(Long tenantId, String wabaId, String phoneNumberId);
    
    Optional<WhatsAppAccount> findFirstByTenantIdAndWabaIdOrderByIdAsc(Long tenantId, String wabaId);
    
    default Optional<WhatsAppAccount> findByTenantId(Long tenantId) {
        List<WhatsAppAccount> accounts = findAllByTenantId(tenantId);
        return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
    }
    
    List<WhatsAppAccount> findAllByTenantId(Long tenantId);
    List<WhatsAppAccount> findAllByWabaId(String wabaId);
    boolean existsByPhoneNumberId(String phoneNumberId);
    Optional<WhatsAppAccount> findByWabaId(String wabaId);
    List<WhatsAppAccount> findAllByBusinessId(String businessId);
    List<WhatsAppAccount> findAllByStatus(WhatsAppAccount.Status status);
    boolean existsByWabaId(String wabaId);
    
 // Add this method to find account by phoneNumberId
    Optional<WhatsAppAccount> findByPhoneNumberId(String phoneNumberId);
    
    // ADD THIS METHOD
    @Query("SELECT a.displayPhoneNumber FROM WhatsAppAccount a WHERE a.phoneNumberId = :phoneNumberId")
    String findDisplayPhoneNumberByPhoneNumberId(@Param("phoneNumberId") String phoneNumberId);
}