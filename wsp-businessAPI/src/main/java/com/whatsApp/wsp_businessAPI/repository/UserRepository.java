package com.whatsApp.wsp_businessAPI.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT MAX(u.tenantId) FROM User u")
    Optional<Long> findMaxTenantId();

	Optional<User> findByTenantId(Long tenantId);
	
	Optional<User> findByEmailOrPhoneNumber(String email, String phoneNumber);
	Optional<User> findByPhoneNumber(String phoneNumber);
	boolean existsByPhoneNumber(String phoneNumber);
}
