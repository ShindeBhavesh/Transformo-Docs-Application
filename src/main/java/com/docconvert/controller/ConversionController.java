package com.docconvert.controller;

import com.docconvert.dto.FileDTOs.*;
import com.docconvert.entity.User;
import com.docconvert.entity.UserFile;
import com.docconvert.service.ConversionService;
import com.docconvert.service.FileStorageService;
import com.docconvert.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Conversions", description = "Document conversion endpoints")
public class ConversionController {

    private final ConversionService conversionService;
    private final FileStorageService fileStorageService;
    private final UserService userService;

    @DeleteMapping("/history")
    @Operation(summary = "Clear conversion history", description = "Deletes all conversion history for the current user")
    public ResponseEntity<ApiResponse<String>> clearHistory(Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            long deletedCount = conversionService.clearUserHistory(user);
            return ResponseEntity.ok(ApiResponse.success("Cleared " + deletedCount + " history records", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to clear history: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/word-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert Word to PDF",
            description = "Converts Word documents (.doc, .docx) to PDF format with formatting preservation")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Conversion successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or conversion failed")
    })
    public ResponseEntity<ApiResponse<ConversionResponse>> wordToPdf(
            @Parameter(description = "Word document file (.doc or .docx)", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.wordToPdf(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/pdf-to-word", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert PDF to Word",
            description = "Converts PDF documents to Word format (.docx)")
    public ResponseEntity<ApiResponse<ConversionResponse>> pdfToWord(
            @Parameter(description = "PDF file", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.pdfToWord(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/compress-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Compress PDF",
            description = "Compresses PDF files to reduce file size. Levels: low, medium, high")
    public ResponseEntity<ApiResponse<ConversionResponse>> compressPdf(
            @Parameter(description = "PDF file to compress", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Compression level: low, medium (default), or high")
            @RequestParam(value = "level", defaultValue = "medium") String level,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.compressPdf(userFile, user, level);
            return ResponseEntity.ok(ApiResponse.success("Compression successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Compression failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/excel-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert Excel to PDF",
            description = "Converts Excel spreadsheets (.xls, .xlsx) to PDF with table formatting")
    public ResponseEntity<ApiResponse<ConversionResponse>> excelToPdf(
            @Parameter(description = "Excel file", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.excelToPdf(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/ppt-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert PowerPoint to PDF",
            description = "Converts PowerPoint presentations (.ppt, .pptx) to PDF")
    public ResponseEntity<ApiResponse<ConversionResponse>> pptToPdf(
            @Parameter(description = "PowerPoint file", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.pptToPdf(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/jpg-to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert Images to PDF",
            description = "Converts multiple image files to a single PDF document")
    public ResponseEntity<ApiResponse<ConversionResponse>> jpgToPdf(
            @Parameter(description = "Image files (JPG, PNG, etc.)", required = true)
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            List<UserFile> userFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                userFiles.add(fileStorageService.storeFile(file, user));
            }
            ConversionResponse response = conversionService.jpgToPdf(userFiles, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/pdf-ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract text from PDF/Image (OCR)",
            description = "Uses OCR to extract text from scanned PDFs or images")
    public ResponseEntity<ApiResponse<OcrResponse>> pdfOcr(
            @Parameter(description = "PDF or image file", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            OcrResponse response = conversionService.performOcr(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("OCR successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("OCR failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/merge-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Merge multiple PDFs",
            description = "Combines multiple PDF files into a single document")
    public ResponseEntity<ApiResponse<ConversionResponse>> mergePdfs(
            @Parameter(description = "PDF files to merge", required = true)
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            List<UserFile> userFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                userFiles.add(fileStorageService.storeFile(file, user));
            }
            ConversionResponse response = conversionService.mergePdfs(userFiles, user);
            return ResponseEntity.ok(ApiResponse.success("Merge successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Merge failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/split-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Split PDF",
            description = "Extracts specified pages from a PDF. Use format: 1,3,5-10")
    public ResponseEntity<ApiResponse<ConversionResponse>> splitPdf(
            @Parameter(description = "PDF file", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Page range (e.g., '1,3,5-10')", required = true)
            @RequestParam("pageRange") String pageRange,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.splitPdf(userFile, user, pageRange);
            return ResponseEntity.ok(ApiResponse.success("Split successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Split failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/delete-pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Delete pages from PDF",
            description = "Removes specified pages from a PDF document")
    public ResponseEntity<ApiResponse<ConversionResponse>> deletePages(
            @Parameter(description = "PDF file", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Pages to delete (e.g., '1,3,5-10')", required = true)
            @RequestParam("pageRange") String pageRange,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.deletePdfPages(userFile, user, pageRange);
            return ResponseEntity.ok(ApiResponse.success("Pages deleted successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Delete pages failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/pdf-to-ppt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert PDF to PowerPoint",
            description = "Converts PDF pages to PowerPoint slides")
    public ResponseEntity<ApiResponse<ConversionResponse>> pdfToPpt(
            @Parameter(description = "PDF file", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.pdfToPpt(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/pdf-to-jpg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Convert PDF to Images",
            description = "Converts all PDF pages to JPG images (returned as ZIP)")
    public ResponseEntity<ApiResponse<ConversionResponse>> pdfToJpg(
            @Parameter(description = "PDF file", required = true)
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);
            ConversionResponse response = conversionService.pdfToJpg(userFile, user);
            return ResponseEntity.ok(ApiResponse.success("Conversion successful", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Conversion failed: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    @Operation(summary = "Get conversion history",
            description = "Returns list of all conversions performed by the user")
    public ResponseEntity<ApiResponse<List<ConversionResponse>>> getHistory(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        List<ConversionResponse> history = conversionService.getConversionHistory(user);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get conversion statistics",
            description = "Returns conversion statistics for the current user")
    public ResponseEntity<ApiResponse<DashboardStats>> getStats(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        DashboardStats stats = conversionService.getStats(user);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    // Helper methods remain the same
    private void validateFileOwnership(UserFile file, User user) {
        if (!file.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied: You don't have permission to access this file");
        }
    }
}