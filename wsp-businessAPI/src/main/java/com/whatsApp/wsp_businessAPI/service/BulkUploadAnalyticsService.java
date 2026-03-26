package com.whatsApp.wsp_businessAPI.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.whatsApp.wsp_businessAPI.entity.WhatsAppMessage;
import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;
import com.whatsApp.wsp_businessAPI.repository.WhatsAppMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkUploadAnalyticsService {

    private final WhatsAppMessageRepository messageRepository;
    private final FileUploader fileUploader;

    private static final String ANALYTICS_DIR = "analytics";
    private static final DateTimeFormatter FILENAME_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Generate analytics Excel file for a campaign
     * @param campaignId Campaign ID
     * @param tenantId Tenant ID (for security)
     * @return URL of the generated analytics file
     */
    public String generateCampaignAnalytics(Long campaignId, Long tenantId) {
        
        log.info("Generating analytics for campaign: {}, tenant: {}", campaignId, tenantId);

        // Fetch all messages for this campaign
        List<WhatsAppMessage> messages = messageRepository.findAllByCampaignId(campaignId);
        
        if (messages.isEmpty()) {
            throw new ValidationException(
                "NO_MESSAGES_FOUND",
                "No messages found for campaign ID: " + campaignId
            );
        }

        // Verify tenant ownership (security check)
        boolean belongsToTenant = messages.stream()
            .allMatch(msg -> msg.getTenantId().equals(tenantId));
        
        if (!belongsToTenant) {
            throw new ValidationException(
                "UNAUTHORIZED",
                "You don't have permission to access this campaign"
            );
        }

        // Create analytics directory if not exists
        File analyticsDir = new File(ANALYTICS_DIR);
        if (!analyticsDir.exists()) {
            analyticsDir.mkdirs();
        }

        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(FILENAME_FORMAT);
        File analyticsFile = new File(analyticsDir, 
            String.format("campaign_%d_%s.xlsx", campaignId, timestamp));

        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(analyticsFile)) {

            // Create sheet
            Sheet sheet = workbook.createSheet("Campaign Analytics");
            
            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            // Create status-based cell styles
            CellStyle successStyle = createStatusStyle(workbook, IndexedColors.LIGHT_GREEN);
            CellStyle pendingStyle = createStatusStyle(workbook, IndexedColors.LIGHT_YELLOW);
            CellStyle failedStyle = createStatusStyle(workbook, IndexedColors.LIGHT_BLUE	);
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "S.No", "Phone Number", "Status", "Message ID", 
                "Delivered At", "Read At", "Customer Replied", "Failure Reason"
            };
            
            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Populate data rows
            int rowNum = 1;
            int successCount = 0, pendingCount = 0, failedCount = 0;
            
            for (WhatsAppMessage msg : messages) {
                Row row = sheet.createRow(rowNum++);
                
                // S.No
                row.createCell(0).setCellValue(rowNum - 1);
                
                // Phone Number
                row.createCell(1).setCellValue(msg.getPhoneNumber());
                
                // Status with color coding
                Cell statusCell = row.createCell(2);
                statusCell.setCellValue(msg.getStatus().name());
                
                // Apply status-based styling
                switch (msg.getStatus()) {
                    case DELIVERED:
                    case READ:
                    case SENT:
                        statusCell.setCellStyle(successStyle);
                        successCount++;
                        break;
                    case QUEUED:
                    case PENDING:
                        statusCell.setCellStyle(pendingStyle);
                        pendingCount++;
                        break;
                    case FAILED:
                        statusCell.setCellStyle(failedStyle);
                        failedCount++;
                        break;
                    default:
                        break;
                }
                
                // Message ID
                row.createCell(3).setCellValue(
                    msg.getMetaMessageId() != null ? msg.getMetaMessageId() : "N/A"
                );
                
                // Delivered At
                row.createCell(4).setCellValue(
                    msg.getDeliveredAt() != null ? msg.getDeliveredAt().toString() : "-"
                );
                
                // Read At
                row.createCell(5).setCellValue(
                    msg.getReadAt() != null ? msg.getReadAt().toString() : "-"
                );
                
                // Customer Replied
                row.createCell(6).setCellValue(
                    msg.getCustomerReplied() != null && msg.getCustomerReplied() ? "Yes" : "No"
                );
                
                // Failure Reason
                row.createCell(7).setCellValue(
                    msg.getFailureReason() != null ? msg.getFailureReason() : "-"
                );
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Create summary sheet
            createSummarySheet(workbook, campaignId, messages.size(), 
                successCount, pendingCount, failedCount);

            workbook.write(fos);
            
            log.info("Analytics file generated: {} with {} rows", 
                analyticsFile.getName(), messages.size());

        } catch (IOException e) {
            log.error("Failed to generate analytics file for campaign: {}", campaignId, e);
            throw new ValidationException(
                "ANALYTICS_GENERATION_FAILED",
                "Could not create analytics file: " + e.getMessage()
            );
        }

        // Upload file using FileUploader and return URL
        String fileUrl = fileUploader.handleFileUpload(analyticsFile);
        log.info("Analytics file uploaded: {}", fileUrl);
        
        // Optional: Delete local file after upload
        boolean deleted = analyticsFile.delete();
        if (deleted) {
            log.info("Local analytics file deleted: {}", analyticsFile.getName());
        }
        
        return fileUrl;
    }

    /**
     * Create header cell style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Create status-based cell style
     */
    private CellStyle createStatusStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Create summary sheet with campaign statistics
     */
    private void createSummarySheet(Workbook workbook, Long campaignId, 
            int total, int success, int pending, int failed) {
        
        Sheet sheet = workbook.createSheet("Summary");
        
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Campaign Analytics Summary");
        
        Row campaignRow = sheet.createRow(2);
        campaignRow.createCell(0).setCellValue("Campaign ID:");
        campaignRow.createCell(1).setCellValue(campaignId);
        
        Row totalRow = sheet.createRow(3);
        totalRow.createCell(0).setCellValue("Total Messages:");
        totalRow.createCell(1).setCellValue(total);
        
        Row successRow = sheet.createRow(4);
        successRow.createCell(0).setCellValue("Successful:");
        successRow.createCell(1).setCellValue(success);
        successRow.createCell(2).setCellValue(String.format("%.1f%%", 
            total > 0 ? (success * 100.0 / total) : 0));
        
        Row pendingRow = sheet.createRow(5);
        pendingRow.createCell(0).setCellValue("In Progress:");
        pendingRow.createCell(1).setCellValue(pending);
        pendingRow.createCell(2).setCellValue(String.format("%.1f%%", 
            total > 0 ? (pending * 100.0 / total) : 0));
        
        Row failedRow = sheet.createRow(6);
        failedRow.createCell(0).setCellValue("Failed:");
        failedRow.createCell(1).setCellValue(failed);
        failedRow.createCell(2).setCellValue(String.format("%.1f%%", 
            total > 0 ? (failed * 100.0 / total) : 0));
        
        Row generatedRow = sheet.createRow(8);
        generatedRow.createCell(0).setCellValue("Report Generated:");
        generatedRow.createCell(1).setCellValue(LocalDateTime.now().toString());
        
        // Auto-size columns
        for (int i = 0; i <= 2; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}