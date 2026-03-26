package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.dto.ObaApplicationRequest;
import com.whatsApp.wsp_businessAPI.service.ObaApplicationService;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/whatsapp/oba")
@RequiredArgsConstructor
public class WhatsAppObaController {

    private final ObaApplicationService obaService;

    // APPLY BLUE TICK
    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestBody @Valid ObaApplicationRequest request) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "OBA application submitted",
                        obaService.submitApplication(request)
                )
        );
    }

    // CHECK STATUS
    @GetMapping("/status")
    public ResponseEntity<?> status() {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "OBA status",
                        obaService.getObaStatus()
                )
        );
    }
}
