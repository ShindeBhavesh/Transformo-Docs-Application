package com.docconvert.dto;

import lombok.*;
import java.util.Map;
import java.util.List;

public class MetadataDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileMetadata {
        private Long fileId;
        private String fileName;
        private Long fileSize;
        private String formattedSize;
        private String mimeType;
        private String fileType;

        // Common metadata
        private String title;
        private String author;
        private String subject;
        private String keywords;
        private String creator;
        private String producer;
        private String creationDate;
        private String modificationDate;

        // Document specific
        private Integer pageCount;
        private Integer wordCount;
        private Integer characterCount;
        private Integer paragraphCount;

        // PDF specific
        private String pdfVersion;
        private Boolean isEncrypted;
        private Boolean isTagged;
        private String pageSize;

        // Image specific
        private Integer width;
        private Integer height;
        private String colorSpace;
        private Integer dpi;
        private String compression;

        // Excel specific
        private Integer sheetCount;
        private List<String> sheetNames;

        // PPT specific
        private Integer slideCount;
        private Boolean hasNotes;

        // Custom properties
        private Map<String, String> customProperties;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataUpdateRequest {
        private String title;
        private String author;
        private String subject;
        private String keywords;
        private Map<String, String> customProperties;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchMetadataResponse {
        private List<FileMetadata> files;
        private Integer totalFiles;
        private Integer successCount;
        private Integer failureCount;
    }
}