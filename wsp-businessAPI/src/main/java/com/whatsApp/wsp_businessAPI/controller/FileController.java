package com.whatsApp.wsp_businessAPI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsApp.wsp_businessAPI.service.FileUploader;
import com.whatsApp.wsp_businessAPI.util.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileUploader fileUploader;

    @GetMapping("/{filename:.+}")
    public ResponseEntity<ApiResponse<?>> getFileUrl(@PathVariable String filename) {
        String fileUrl = fileUploader.getFileUrl(filename);
        return ResponseEntity.ok(ApiResponse.success("File URL retrieved", 
            Map.of("fileUrl", fileUrl)));
    }
}