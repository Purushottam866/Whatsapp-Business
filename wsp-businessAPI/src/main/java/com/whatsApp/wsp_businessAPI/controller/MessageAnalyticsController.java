package com.whatsApp.wsp_businessAPI.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.service.MessageAnalyticsService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class MessageAnalyticsController {
    
    private final MessageAnalyticsService analyticsService;
    
    // ===== EXISTING ENDPOINTS =====
    
    @GetMapping("/waba/{wabaId}/summary")
    public ResponseEntity<?> getWabaSummary(@PathVariable String wabaId) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Analytics summary", 
                analyticsService.getWabaSummary(wabaId)
            )
        );
    }
    
    @GetMapping("/waba/{wabaId}/phone/{phoneNumberId}/summary")
    public ResponseEntity<?> getPhoneNumberSummary(
            @PathVariable String wabaId,
            @PathVariable String phoneNumberId) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Phone number analytics", 
                analyticsService.getPhoneNumberSummary(wabaId, phoneNumberId)
            )
        );
    }
    
    @GetMapping("/waba/{wabaId}/phones/summary")
    public ResponseEntity<?> getAllPhoneNumbersSummary(@PathVariable String wabaId) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "All phone numbers analytics", 
                analyticsService.getAllPhoneNumbersSummary(wabaId)
            )
        );
    }
    
    // ===== NEW REPLY ANALYTICS ENDPOINTS =====	 
    
    /**
     * Get all customer replies for the current tenant
     */
    @GetMapping("/replies")
    public ResponseEntity<?> getAllReplies() {
        return ResponseEntity.ok(
            ApiResponse.success(
                "All customer replies", 
                analyticsService.getAllReplies()
            )
        );
    }
    
    /**
     * Get replies for a specific campaign
     */
    @GetMapping("/replies/campaign/{campaignId}")
    public ResponseEntity<?> getCampaignReplies(@PathVariable Long campaignId) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Campaign replies", 
                analyticsService.getCampaignReplies(campaignId)
            )
        ); 
    }
    
    /**
     * Get replies for a specific phone number (your WhatsApp number)
     */
    @GetMapping("/replies/phone/{phoneNumberId}")
    public ResponseEntity<?> getPhoneNumberReplies(@PathVariable String phoneNumberId) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Phone number replies", 
                analyticsService.getPhoneNumberReplies(phoneNumberId)
            )
        );
    }
    
    /**
     * Get replies for a specific template
     */
    @GetMapping("/replies/template/{templateName}")
    public ResponseEntity<?> getTemplateReplies(
            @PathVariable String templateName,
            @RequestParam(required = false) String wabaId) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Template replies", 
                analyticsService.getTemplateReplies(templateName, wabaId)
            )
        );
    }
    
    /**
     * Search replies by keyword
     */
    @GetMapping("/replies/search")
    public ResponseEntity<?> searchReplies(@RequestParam String keyword) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Search results", 
                analyticsService.searchReplies(keyword)
            )
        );
    }
    
    /**
     * Get replies within a date range
     */
    @GetMapping("/replies/date-range")
    public ResponseEntity<?> getRepliesByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Replies in date range", 
                analyticsService.getRepliesByDateRange(startDate, endDate)
            )
        );
    }
    
    /**
     * Get reply statistics summary
     */
    @GetMapping("/replies/stats")
    public ResponseEntity<?> getReplyStats() {
        return ResponseEntity.ok(
            ApiResponse.success(
                "Reply statistics", 
                analyticsService.getReplyStats()
            )
        );
    }
}