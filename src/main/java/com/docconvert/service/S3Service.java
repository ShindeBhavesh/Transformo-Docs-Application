package com.docconvert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

@Service
@Slf4j
public class S3Service {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.access-key}")
    private String accessKey;

    @Value("${aws.secret-key}")
    private String secretKey;

    private S3Client s3Client;
    private boolean isConfigured = false;

    @PostConstruct
    public void init() {
        try {
            if (accessKey != null && !accessKey.startsWith("YOUR_") && 
                secretKey != null && !secretKey.startsWith("YOUR_")) {
                
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
                
                s3Client = S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .build();
                
                isConfigured = true;
                log.info("AWS S3 client initialized successfully");
            } else {
                log.warn("AWS S3 credentials not configured. Using local storage only.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize S3 client: {}", e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file, Long userId, String fileName) throws IOException {
        if (!isConfigured) {
            log.warn("S3 not configured, skipping upload");
            return null;
        }

        String key = String.format("users/%d/uploads/%s", userId, fileName);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            log.info("File uploaded to S3: {}", key);
            return key;
        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new RuntimeException("Failed to upload to S3", e);
        }
    }

    public String uploadBytes(byte[] bytes, Long userId, String fileName, String contentType) {
        if (!isConfigured) {
            log.warn("S3 not configured, skipping upload");
            return null;
        }

        String key = String.format("users/%d/converted/%s", userId, fileName);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            log.info("Converted file uploaded to S3: {}", key);
            return key;
        } catch (Exception e) {
            log.error("S3 upload failed: {}", e.getMessage());
            throw new RuntimeException("Failed to upload to S3", e);
        }
    }

    public byte[] downloadFile(String key) {
        if (!isConfigured) {
            throw new RuntimeException("S3 not configured");
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (Exception e) {
            log.error("S3 download failed: {}", e.getMessage());
            throw new RuntimeException("Failed to download from S3", e);
        }
    }

    public void deleteFile(String key) {
        if (!isConfigured) {
            log.warn("S3 not configured, skipping delete");
            return;
        }

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("File deleted from S3: {}", key);
        } catch (Exception e) {
            log.error("S3 delete failed: {}", e.getMessage());
        }
    }

    public String getFileUrl(String key) {
        if (!isConfigured || key == null) {
            return null;
        }
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
    }

    public boolean isConfigured() {
        return isConfigured;
    }
}
