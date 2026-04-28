package com.docconvert.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversion_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_id")
    private UserFile sourceFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_file_id")
    private UserFile convertedFile;

    @Column(name = "conversion_type", nullable = false, length = 50)
    private String conversionType;

    @Column(name = "source_format", nullable = false, length = 20)
    private String sourceFormat;

    @Column(name = "target_format", nullable = false, length = 20)
    private String targetFormat;

    @Column(name = "source_file_name", nullable = false)
    private String sourceFileName;

    @Column(name = "converted_file_name")
    private String convertedFileName;

    @Column(name = "source_file_size")
    private Long sourceFileSize;

    @Column(name = "converted_file_size")
    private Long convertedFileSize;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConversionStatus status = ConversionStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
    }

    public enum ConversionStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}