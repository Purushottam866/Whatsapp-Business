package com.whatsApp.wsp_businessAPI.dto;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ObaApplicationRequest {

    @NotBlank
    private String businessWebsiteUrl;

    @NotBlank
    private String primaryCountryOfOperation;

    @NotBlank
    private String primaryLanguage;

    @NotEmpty
    private List<String> supportingLinks;

    private String additionalSupportingInformation;
}
