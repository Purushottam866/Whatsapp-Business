package com.whatsApp.wsp_businessAPI.dto;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class CreateTemplateRequest {

    @NotBlank
    @Pattern(
        regexp = "^[a-z0-9_]+$",
        message = "Template name must be lowercase and may contain only numbers and underscores"
    )
    private String name;

    @NotNull
    private WhatsAppTemplate.Category category;

    @NotBlank
    private String language;

    @NotNull(message = "components are required")
    private JsonNode components;
}