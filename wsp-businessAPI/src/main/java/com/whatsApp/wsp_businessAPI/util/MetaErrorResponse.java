package com.whatsApp.wsp_businessAPI.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaErrorResponse {

    private String type;
    private String message;
    private Integer code;
    private String fbtraceId;
}
