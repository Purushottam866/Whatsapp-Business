package com.whatsApp.wsp_businessAPI.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.whatsApp.wsp_businessAPI.entity.TemplateImage;

@Repository
public interface TemplateImageRepository extends JpaRepository<TemplateImage, Long> {
    
    Optional<TemplateImage> findByTemplateId(Long templateId);
    
    void deleteByTemplateId(Long templateId);
}