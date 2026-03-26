package com.whatsApp.wsp_businessAPI.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendMessageRequest {
    
    @NotBlank(message = "Recipient phone number is required")
    @Pattern(
            regexp = "^[1-9][0-9]{7,14}$",
            message = "Phone number must be in international format WITHOUT +. Example: 919876543210"
    )
    private String to;
    
    // For text messages
    private String text;
    
    // For media messages
    private String type; // image, audio, document, video
    private String mediaUrl;
    private String caption; // for images and videos
    private String fileName; // for documents
    
    // Helper method to determine if this is a media message
    public boolean isMediaMessage() {
        return type != null && !type.isEmpty() && mediaUrl != null && !mediaUrl.isEmpty();
    }
    
    // Helper method to determine if this is a text message
    public boolean isTextMessage() {
        return text != null && !text.isEmpty() && !isMediaMessage();
    }
    
    // Validation: either text or media must be provided, but not both
    public boolean isValid() {
        return (isTextMessage() || isMediaMessage()) && !(isTextMessage() && isMediaMessage());
    }
}