package com.whatsApp.wsp_businessAPI.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.whatsApp.wsp_businessAPI.exceptions.ValidationException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileParserService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_NUMBERS_PER_FILE = 10000; // Safety limit
    
    // Phone column keywords (more specific patterns)
    private static final Set<String> PHONE_COLUMN_KEYWORDS = new HashSet<>(Arrays.asList(
        "phone", "phones",
        "mobile", "mobiles",
        "cell", "cells",
        "telephone", "telephones",
        "whatsapp",
        "contact no", "contact number", "contact numbers",
        "phone no", "phone num", "phone number", "phone numbers",
        "mobile no", "mobile num", "mobile number", "mobile numbers",
        "mob", "mob no", "mob number"
    ));
    
    // Exclude these columns - they contain names, not phone numbers
    private static final Set<String> EXCLUDED_COLUMNS = new HashSet<>(Arrays.asList(
        "contact person", "contact name", "name", "full name", 
        "first name", "last name", "customer name", "client name",
        "person", "representative", "contact_person", "contactperson",
        "contact person name", "person name", "attendee name",
        "participant name", "delegate name", "sponsor name"
    ));

    /**
     * Extract and validate phone numbers from uploaded file
     * Supports multiple phone columns - takes first non-empty value from any matching column
     * @throws InvalidFormatException 
     */
    public List<String> extractPhoneNumbers(MultipartFile file) throws InvalidFormatException {
        
        validateFile(file);
        
        String fileName = file.getOriginalFilename();
        log.info("Processing file: {}, size: {} bytes", fileName, file.getSize());
        
        List<String> rawNumbers = new ArrayList<>();
        
        try {
            File tempFile = File.createTempFile("bulk_upload_", "_" + fileName);
            file.transferTo(tempFile);
            
            if (fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls")) {
                rawNumbers = parseExcel(tempFile);
            } else if (fileName.toLowerCase().endsWith(".csv")) {
                rawNumbers = parseCsv(tempFile);
            } else {
                throw new ValidationException(
                    "INVALID_FILE_TYPE",
                    "Only Excel (.xlsx, .xls) or CSV files are supported. Uploaded: " + fileName
                );
            }
            
            tempFile.delete();
            
        } catch (IOException e) {
            log.error("File processing failed: {}", e.getMessage(), e);
            throw new ValidationException(
                "FILE_PROCESSING_ERROR",
                "Could not process the uploaded file: " + e.getMessage()
            );
        }
        
        // Clean and validate numbers
        List<String> validatedNumbers = validateAndCleanNumbers(rawNumbers);
        
        if (validatedNumbers.isEmpty()) {
            throw new ValidationException(
                "NO_VALID_NUMBERS",
                "No valid phone numbers found in the file. Please check the file format."
            );
        }
        
        log.info("Successfully extracted {} valid phone numbers from file", validatedNumbers.size());
        return validatedNumbers;
    }
    
    /**
     * Validate file size, type, and content
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(
                "EMPTY_FILE",
                "Please upload a non-empty file"
            );
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException(
                "FILE_TOO_LARGE",
                String.format("File size exceeds %d MB limit. Uploaded: %.2f MB", 
                    MAX_FILE_SIZE / (1024 * 1024), file.getSize() / (1024.0 * 1024.0))
            );
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new ValidationException(
                "INVALID_FILE_NAME",
                "File name is invalid"
            );
        }
    }
    
    /**
     * Parse Excel file and extract raw numbers from multiple phone columns
     * @throws InvalidFormatException 
     */
    private List<String> parseExcel(File file) throws IOException, InvalidFormatException {
        List<String> numbers = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();
        
        try (Workbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new ValidationException("EMPTY_SHEET", "Excel sheet has no data rows");
            }
            
            // Find header row
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                throw new ValidationException("NO_HEADER_ROW", "Could not find a header row");
            }
            
            // Find ALL phone-related columns (excluding name columns)
            List<Integer> phoneColumns = findAllPhoneColumns(headerRow);
            
            if (phoneColumns.isEmpty()) {
                throw new ValidationException(
                    "PHONE_COLUMN_NOT_FOUND",
                    "Could not find any phone number column. Expected headers: " + PHONE_COLUMN_KEYWORDS
                );
            }
            
            log.info("Found phone columns at indices: {} with headers: {}", 
                phoneColumns, getColumnHeaders(headerRow, phoneColumns));
            
            // Extract numbers from data rows - try each column until we find a value
            int dataRowStart = headerRow.getRowNum() + 1;
            int rowsProcessed = 0;
            
            for (int i = dataRowStart; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                String phoneNumber = null;
                
                // Try each phone column in order until we find a non-empty value
                for (int colIdx : phoneColumns) {
                    Cell cell = row.getCell(colIdx);
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        String value = dataFormatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            phoneNumber = value;
                            log.debug("Found number in column {}: {}", colIdx, value);
                            break; // Take first non-empty value
                        }
                    }
                }
                
                if (phoneNumber != null) {
                    numbers.add(phoneNumber);
                    rowsProcessed++;
                }
                
                // Safety check
                if (numbers.size() > MAX_NUMBERS_PER_FILE) {
                    throw new ValidationException(
                        "TOO_MANY_NUMBERS",
                        String.format("File contains more than %d numbers. Please split into multiple files.", MAX_NUMBERS_PER_FILE)
                    );
                }
            }
            
            log.info("Processed {} rows with phone numbers out of {} total rows", 
                rowsProcessed, sheet.getLastRowNum() - dataRowStart + 1);
        }
        
        return numbers;
    }
    
    /**
     * Parse CSV file and extract raw numbers from multiple columns
     */
    private List<String> parseCsv(File file) throws IOException {
        List<String> numbers = new ArrayList<>();
        int lineCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            
            // Find header row and phone columns
            List<Integer> phoneColumns = null;
            int headerRowNum = -1;
            
            // Search first 10 rows for headers
            while ((line = reader.readLine()) != null && headerRowNum < 10) {
                lineCount++;
                line = line.trim();
                if (line.isBlank()) continue;
                
                String[] columns = parseCsvLine(line);
                List<Integer> foundColumns = findAllPhoneColumnsInArray(columns);
                
                if (!foundColumns.isEmpty()) {
                    phoneColumns = foundColumns;
                    headerRowNum = lineCount;
                    log.info("Found phone columns at indices {} in row {}: {}", 
                        phoneColumns, lineCount, getColumnHeaders(columns, phoneColumns));
                    break;
                }
            }
            
            if (phoneColumns == null || phoneColumns.isEmpty()) {
                throw new ValidationException(
                    "PHONE_COLUMN_NOT_FOUND",
                    "Could not find any phone number column in CSV. Expected headers: " + PHONE_COLUMN_KEYWORDS
                );
            }
            
            // Process data rows
            while ((line = reader.readLine()) != null) {
                lineCount++;
                line = line.trim();
                if (line.isBlank()) continue;
                
                String[] columns = parseCsvLine(line);
                String phoneNumber = null;
                
                // Try each phone column in order
                for (int colIdx : phoneColumns) {
                    if (columns.length > colIdx) {
                        String value = columns[colIdx].trim();
                        if (!value.isEmpty()) {
                            phoneNumber = value;
                            break;
                        }
                    }
                }
                
                if (phoneNumber != null) {
                    numbers.add(phoneNumber);
                }
                
                // Safety check
                if (numbers.size() > MAX_NUMBERS_PER_FILE) {
                    throw new ValidationException(
                        "TOO_MANY_NUMBERS",
                        String.format("File contains more than %d numbers. Please split into multiple files.", MAX_NUMBERS_PER_FILE)
                    );
                }
            }
            
            log.info("Processed {} CSV lines, extracted {} numbers", lineCount, numbers.size());
        }
        
        return numbers;
    }
    
    /**
     * Find the first row that might contain headers
     */
    private Row findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(5, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row != null && row.getPhysicalNumberOfCells() > 0) {
                // Check if this row contains any phone keyword
                for (Cell cell : row) {
                    String value = getCellValueAsString(cell).toLowerCase();
                    if (PHONE_COLUMN_KEYWORDS.stream().anyMatch(keyword -> 
                        value.contains(keyword.toLowerCase()))) {
                        return row;
                    }
                }
            }
        }
        return sheet.getRow(0); // Fallback to first row
    }
    
    /**
     * Find ALL column indices that contain phone number headers (excluding name columns)
     */
    private List<Integer> findAllPhoneColumns(Row headerRow) {
        List<Integer> phoneColumns = new ArrayList<>();
        
        for (Cell cell : headerRow) {
            String headerValue = getCellValueAsString(cell).toLowerCase().trim();
            
            // Skip if this is an excluded column (contains name-related keywords)
            boolean isExcluded = EXCLUDED_COLUMNS.stream()
                .anyMatch(excluded -> headerValue.contains(excluded));
            
            if (isExcluded) {
                log.debug("Skipping excluded column: '{}'", headerValue);
                continue;
            }
            
            // Check if it's a phone column
            boolean isPhoneColumn = PHONE_COLUMN_KEYWORDS.stream()
                .anyMatch(keyword -> headerValue.contains(keyword.toLowerCase()));
            
            if (isPhoneColumn) {
                phoneColumns.add(cell.getColumnIndex());
                log.debug("Found phone column: '{}' at index {}", headerValue, cell.getColumnIndex());
            }
        }
        
        // Sort to maintain left-to-right order
        phoneColumns.sort(Integer::compareTo);
        return phoneColumns;
    }
    
    /**
     * Find ALL phone column indices in CSV header array (excluding name columns)
     */
    private List<Integer> findAllPhoneColumnsInArray(String[] headers) {
        List<Integer> phoneColumns = new ArrayList<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();
            
            // Skip if this is an excluded column
            boolean isExcluded = EXCLUDED_COLUMNS.stream()
                .anyMatch(excluded -> header.contains(excluded));
            
            if (isExcluded) {
                log.debug("Skipping excluded column: '{}'", header);
                continue;
            }
            
            boolean isPhoneColumn = PHONE_COLUMN_KEYWORDS.stream()
                .anyMatch(keyword -> header.contains(keyword.toLowerCase()));
            
            if (isPhoneColumn) {
                phoneColumns.add(i);
            }
        }
        
        return phoneColumns;
    }
    
    /**
     * Get column headers for logging
     */
    private String getColumnHeaders(Row headerRow, List<Integer> columns) {
        List<String> headers = new ArrayList<>();
        for (int colIdx : columns) {
            Cell cell = headerRow.getCell(colIdx);
            headers.add(getCellValueAsString(cell));
        }
        return String.join(", ", headers);
    }
    
    /**
     * Get column headers from array for logging
     */
    private String getColumnHeaders(String[] allHeaders, List<Integer> columns) {
        List<String> headers = new ArrayList<>();
        for (int colIdx : columns) {
            if (colIdx < allHeaders.length) {
                headers.add(allHeaders[colIdx]);
            }
        }
        return String.join(", ", headers);
    }
    
    /**
     * Parse CSV line handling quotes and commas
     */
    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        
        return tokens.toArray(new String[0]);
    }
    
    /**
     * Clean and validate phone numbers
     * Removes ALL non-digit characters (parentheses, hyphens, spaces, etc.)
     */
    private List<String> validateAndCleanNumbers(List<String> rawNumbers) {
        List<String> validated = new ArrayList<>();
        List<String> invalidNumbers = new ArrayList<>();
        
        for (String raw : rawNumbers) {
            if (raw == null || raw.isBlank()) continue;
            
            // Step 1: Remove ALL non-digit characters (keeps only 0-9)
            String cleaned = raw.replaceAll("[^0-9]", "");
            
            // Log the transformation for debugging
            if (!raw.equals(cleaned)) {
                log.debug("Phone number cleaned: '{}' → '{}'", raw, cleaned);
            }
            
            // Step 2: If empty after cleaning, skip
            if (cleaned.isEmpty()) {
                invalidNumbers.add(raw + " → no digits found");
                continue;
            }
            
            // Step 3: Remove leading zero if number is longer than 10 digits
            if (cleaned.startsWith("0") && cleaned.length() > 10) {
                cleaned = cleaned.substring(1);
            }
            
            // Step 4: Validate length (8-15 digits international)
            if (cleaned.length() < 8 || cleaned.length() > 15) {
                invalidNumbers.add(raw + " → cleaned to: " + cleaned + " (invalid length: " + cleaned.length() + " digits)");
                continue;
            }
            
            // Step 5: If Indian number without country code, add 91
            if (cleaned.length() == 10 && 
                (cleaned.startsWith("6") || cleaned.startsWith("7") || 
                 cleaned.startsWith("8") || cleaned.startsWith("9"))) {
                cleaned = "91" + cleaned;
                log.debug("Added India country code: {}", cleaned);
            }
            
            validated.add(cleaned);
        }
        
        // Log invalid numbers for debugging
        if (!invalidNumbers.isEmpty()) {
            log.warn("Found {} invalid phone numbers:", invalidNumbers.size());
            invalidNumbers.stream().limit(10).forEach(num -> log.warn("  - {}", num));
            if (invalidNumbers.size() > 10) {
                log.warn("  ... and {} more", invalidNumbers.size() - 10);
            }
        }
        
        log.info("Validation complete: {} valid, {} invalid", validated.size(), invalidNumbers.size());
        return validated;
    }
    
    /**
     * Safely get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue)) {
                        return String.valueOf((long) numericValue);
                    }
                    return String.valueOf(numericValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf((long) cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }
}