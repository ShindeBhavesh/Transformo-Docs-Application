package com.docconvert.dto;

import lombok.*;
import java.util.List;

public class FileDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileResponse {
        private Long id;
        private String fileName;
        private String originalName;
        private Long fileSize;
        private String fileType;
        private String mimeType;
        private String s3Url;
        private Boolean isConverted;
        private String uploadedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConversionRequest {
        private String conversionType;
        private String pageRange;
        private String compressionLevel;
        private List<Long> fileIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConversionResponse {
        private Long id;
        private String conversionType;
        private String sourceFileName;
        private String convertedFileName;
        private Long sourceFileSize;
        private Long convertedFileSize;
        private String status;
        private String downloadUrl;
        private String startedAt;
        private String completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShareRequest {
        private Long fileId;
        private Integer expirationHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShareResponse {
        private String shareToken;
        private String shareUrl;
        private String expiresAt;
        private String fileName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OcrResponse {
        private Long id;
        private String extractedText;
        private Double confidence;
        private String language;
        private Long processingTimeMs;
        private String sourceFileName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DashboardStats {
        private Long totalFiles;
        private Long totalConversions;
        private Long totalStorage;
        private Long ocrScans;
        private String formattedStorage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }
}
