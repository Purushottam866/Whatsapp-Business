package com.whatsApp.wsp_businessAPI.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CampaignStatusResponse {

    private Long campaignId;
    private String status;

    private long total;
    private long queued;
    private long pending;    // ✅ ADD THIS
    private long sent;
    private long delivered;
    private long read;
    private long failed;

    private double progressPercentage;
}