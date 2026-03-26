package com.whatsApp.wsp_businessAPI.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileUploader {

    private static final int SFTP_PORT = 22;
    
    @Value("${sftp.user:dh_gmj3vr}")
    private String sftpUser;
    
    @Value("${sftp.password:Srikrishna@0700}")
    private String sftpPassword;
    
    @Value("${sftp.host:pdx1-shared-a2-03.dreamhost.com}")
    private String sftpHost;
    
    @Value("${sftp.directory:/home/dh_gmj3vr/mantramatrix.in/whatsapp_analytics/}")
    private String sftpDirectory;
    
    @Value("${file.base-url:https://mantramatrix.in/whatsapp_analytics/}")
    private String baseUrl;

    /**
     * Handle file upload from MultipartFile
     */
    public String handleFileUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("EMPTY_FILE", "Cannot upload empty file");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            
            byte[] fileBytes = file.getBytes();
            String fileUrl = uploadToSftp(fileBytes, uniqueFilename);
            
            log.info("File uploaded: {} -> {}", originalFilename, fileUrl);
            return fileUrl;
            
        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            throw new ValidationException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * Handle file upload from File object
     */
    public String handleFileUpload(File file) {
        if (file == null || !file.exists()) {
            throw new ValidationException("FILE_NOT_FOUND", "File does not exist");
        }

        try {
            String originalFilename = file.getName();
            String extension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + extension;
            
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String fileUrl = uploadToSftp(fileBytes, uniqueFilename);
            
            log.info("File uploaded: {} -> {}", originalFilename, fileUrl);
            return fileUrl;
            
        } catch (IOException e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            throw new ValidationException("UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * Upload bytes to SFTP - using exact directory without creating subfolders
     */
    private String uploadToSftp(byte[] fileBytes, String filename) {
        Session session = null;
        ChannelSftp channelSftp = null;
        
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(sftpUser, sftpHost, SFTP_PORT);
            session.setPassword(sftpPassword);
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(30000);
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect(10000);
            
            // Navigate to the directory (assume it exists)
            try {
                channelSftp.cd(sftpDirectory);
            } catch (SftpException e) {
                log.error("Directory not found: {}", sftpDirectory);
                throw new ValidationException("SFTP_DIR_NOT_FOUND", 
                    "Remote directory doesn't exist or no permission: " + sftpDirectory);
            }
            
            // Upload file directly to the directory
            try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
                channelSftp.put(inputStream, filename);
            }
            
            return baseUrl + filename;
            
        } catch (JSchException | SftpException | IOException e) {
            log.error("SFTP upload failed: {}", e.getMessage(), e);
            throw new ValidationException("SFTP_UPLOAD_FAILED", 
                "Failed to upload file: " + e.getMessage());
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * Get file URL
     */
    public String getFileUrl(String filename) {
        return baseUrl + filename;
    }

    /**
     * Extract file extension
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}