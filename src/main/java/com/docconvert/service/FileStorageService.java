package com.docconvert.service;

import com.docconvert.dto.FileDTOs.*;
import com.docconvert.entity.*;
import com.docconvert.repository.*;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FileStorageService {

    private final UserFileRepository fileRepository;
    private final SharedFileRepository sharedFileRepository;
    private final UserActivityLogRepository activityLogRepository;
    private final S3Service s3Service;

    @PersistenceContext
    private EntityManager entityManager;

    public FileStorageService(UserFileRepository fileRepository,
                              SharedFileRepository sharedFileRepository,
                              UserActivityLogRepository activityLogRepository,
                              S3Service s3Service) {
        this.fileRepository = fileRepository;
        this.sharedFileRepository = sharedFileRepository;
        this.activityLogRepository = activityLogRepository;
        this.s3Service = s3Service;
    }

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.converted.dir}")
    private String convertedDir;

    private Path uploadPath;
    private Path convertedPath;

    @PostConstruct
    public void init() {
        try {
            uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            convertedPath = Paths.get(convertedDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);
            Files.createDirectories(convertedPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories", e);
        }
    }

    @Transactional
    public UserFile storeFile(MultipartFile file, User user) throws IOException {
        // Check storage limit
        if (!hasStorageSpace(user, file.getSize())) {
            long remaining = getRemainingStorage(user);
            throw new IOException("Storage limit exceeded. You have " + formatFileSize(remaining) + " remaining. Max storage: " + formatFileSize(MAX_STORAGE_PER_USER));
        }

        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = getFileExtension(originalFileName);
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;

        // Create user-specific directory
        Path userUploadPath = uploadPath.resolve(String.valueOf(user.getId()));
        Files.createDirectories(userUploadPath);

        Path targetLocation = userUploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        // Upload to S3
        String s3Key = null;
        String s3Url = null;
        try {
            s3Key = s3Service.uploadFile(file, user.getId(), uniqueFileName);
            s3Url = s3Service.getFileUrl(s3Key);
        } catch (Exception e) {
            log.warn("S3 upload failed, using local storage only: {}", e.getMessage());
        }

        UserFile userFile = UserFile.builder()
                .user(user)
                .fileName(uniqueFileName)
                .originalName(originalFileName)
                .filePath(targetLocation.toString())
                .fileSize(file.getSize())
                .fileType(fileExtension)
                .mimeType(file.getContentType())
                .s3Key(s3Key)
                .s3Url(s3Url)
                .isConverted(false)
                .build();

        userFile = fileRepository.save(userFile);

        // Log activity
        logActivity(user, "FILE_UPLOAD", "Uploaded file: " + originalFileName);

        return userFile;
    }

    @Transactional
    public UserFile storeConvertedFile(byte[] fileBytes, String fileName, String mimeType, User user) throws IOException {
        String uniqueFileName = UUID.randomUUID() + "_" + fileName;

        // Create user-specific directory
        Path userConvertedPath = convertedPath.resolve(String.valueOf(user.getId()));
        Files.createDirectories(userConvertedPath);

        Path targetLocation = userConvertedPath.resolve(uniqueFileName);
        Files.write(targetLocation, fileBytes);

        // Upload to S3
        String s3Key = null;
        String s3Url = null;
        try {
            s3Key = s3Service.uploadBytes(fileBytes, user.getId(), uniqueFileName, mimeType);
            s3Url = s3Service.getFileUrl(s3Key);
        } catch (Exception e) {
            log.warn("S3 upload failed for converted file: {}", e.getMessage());
        }

        UserFile userFile = UserFile.builder()
                .user(user)
                .fileName(uniqueFileName)
                .originalName(fileName)
                .filePath(targetLocation.toString())
                .fileSize((long) fileBytes.length)
                .fileType(getFileExtension(fileName))
                .mimeType(mimeType)
                .s3Key(s3Key)
                .s3Url(s3Url)
                .isConverted(true)
                .build();

        return fileRepository.save(userFile);
    }

    public Resource loadFileAsResource(Long fileId, User user) {
        UserFile userFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!userFile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        try {
            Path filePath = Paths.get(userFile.getFilePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found", e);
        }
    }

    public Resource loadFileAsResourceByPath(String filePath) {
        try {
            Path path = Paths.get(filePath).normalize();
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found", e);
        }
    }

    public List<FileResponse> getUserFiles(User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return fileRepository.findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream()
                .map(f -> FileResponse.builder()
                        .id(f.getId())
                        .fileName(f.getFileName())
                        .originalName(f.getOriginalName())
                        .fileSize(f.getFileSize())
                        .fileType(f.getFileType())
                        .mimeType(f.getMimeType())
                        .s3Url(f.getS3Url())
                        .isConverted(f.getIsConverted())
                        .uploadedAt(f.getUploadedAt().format(formatter))
                        .build())
                .collect(Collectors.toList());
    }

    public UserFile getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));
    }

    // Storage limit: 10 GB per user (in bytes)
    private static final long MAX_STORAGE_PER_USER = 10L * 1024L * 1024L * 1024L; // 10 GB

    @Transactional
    public void deleteFile(Long fileId, User user) {
        UserFile userFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!userFile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        try {
            Files.deleteIfExists(Paths.get(userFile.getFilePath()));
            if (userFile.getS3Key() != null) {
                s3Service.deleteFile(userFile.getS3Key());
            }
        } catch (IOException e) {
            log.warn("Could not delete file from disk: {}", e.getMessage());
        }

        // Delete shared file references first
        sharedFileRepository.deleteByFileId(fileId);

        // Update OCR results to remove references to this file
        entityManager.createQuery("UPDATE OcrResult o SET o.sourceFile = null WHERE o.sourceFile.id = :fileId")
                .setParameter("fileId", fileId)
                .executeUpdate();

        // Update conversion history to remove references to this file
        entityManager.createQuery("UPDATE ConversionHistory ch SET ch.sourceFile = null WHERE ch.sourceFile.id = :fileId")
                .setParameter("fileId", fileId)
                .executeUpdate();
        entityManager.createQuery("UPDATE ConversionHistory ch SET ch.convertedFile = null WHERE ch.convertedFile.id = :fileId")
                .setParameter("fileId", fileId)
                .executeUpdate();

        fileRepository.delete(userFile);
        logActivity(user, "FILE_DELETE", "Deleted file: " + userFile.getOriginalName());
    }

    // Check if user has enough storage space
    public boolean hasStorageSpace(User user, long fileSize) {
        Long currentUsage = fileRepository.getTotalStorageByUserId(user.getId());
        if (currentUsage == null) currentUsage = 0L;
        return (currentUsage + fileSize) <= MAX_STORAGE_PER_USER;
    }

    public long getRemainingStorage(User user) {
        Long currentUsage = fileRepository.getTotalStorageByUserId(user.getId());
        if (currentUsage == null) currentUsage = 0L;
        return MAX_STORAGE_PER_USER - currentUsage;
    }

    public long getMaxStoragePerUser() {
        return MAX_STORAGE_PER_USER;
    }

    @Transactional
    public ShareResponse shareFile(Long fileId, User user, Integer expirationHours, String baseUrl) {
        UserFile userFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!userFile.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        String shareToken = UUID.randomUUID().toString();
        String shareUrl = baseUrl + "/api/share/public/" + shareToken;

        SharedFile sharedFile = SharedFile.builder()
                .file(userFile)
                .user(user)
                .shareToken(shareToken)
                .shareUrl(shareUrl)
                .isActive(true)
                .accessCount(0)
                .build();

        if (expirationHours != null && expirationHours > 0) {
            sharedFile.setExpiresAt(java.time.LocalDateTime.now().plusHours(expirationHours));
        }

        sharedFileRepository.save(sharedFile);
        logActivity(user, "FILE_SHARE", "Shared file: " + userFile.getOriginalName());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return ShareResponse.builder()
                .shareToken(shareToken)
                .shareUrl(shareUrl)
                .expiresAt(sharedFile.getExpiresAt() != null ? sharedFile.getExpiresAt().format(formatter) : null)
                .fileName(userFile.getOriginalName())
                .build();
    }

    public Resource loadSharedFile(String shareToken) {
        SharedFile sharedFile = sharedFileRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new RuntimeException("Share link not found"));

        if (!sharedFile.getIsActive()) {
            throw new RuntimeException("Share link is no longer active");
        }

        if (sharedFile.getExpiresAt() != null &&
                sharedFile.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Share link has expired");
        }

        sharedFile.setAccessCount(sharedFile.getAccessCount() + 1);
        sharedFileRepository.save(sharedFile);

        return loadFileAsResourceByPath(sharedFile.getFile().getFilePath());
    }

    public String getSharedFileName(String shareToken) {
        SharedFile sharedFile = sharedFileRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new RuntimeException("Share link not found"));
        return sharedFile.getFile().getOriginalName();
    }

    public DashboardStats getDashboardStats(User user) {
        Long totalFiles = fileRepository.countByUserId(user.getId());
        Long totalStorage = fileRepository.getTotalStorageByUserId(user.getId());
        if (totalStorage == null) totalStorage = 0L;

        long remaining = MAX_STORAGE_PER_USER - totalStorage;
        int usagePercent = (int) ((totalStorage * 100) / MAX_STORAGE_PER_USER);

        return DashboardStats.builder()
                .totalFiles(totalFiles)
                .totalStorage(totalStorage)
                .formattedStorage(formatFileSize(totalStorage) + " / " + formatFileSize(MAX_STORAGE_PER_USER))
                .build();
    }

    private void logActivity(User user, String type, String description) {
        UserActivityLog log = UserActivityLog.builder()
                .user(user)
                .activityType(type)
                .description(description)
                .build();
        activityLogRepository.save(log);
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }

    private String formatFileSize(Long bytes) {
        if (bytes == 0) return "0 Bytes";
        String[] sizes = {"Bytes", "KB", "MB", "GB"};
        int i = (int) Math.floor(Math.log(bytes) / Math.log(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, i), sizes[i]);
    }
}