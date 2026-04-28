package com.docconvert.controller;

import com.docconvert.dto.FileDTOs.*;
import com.docconvert.entity.User;
import com.docconvert.entity.UserFile;
import com.docconvert.service.FileStorageService;
import com.docconvert.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    private final FileStorageService fileStorageService;
    private final UserService userService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<FileResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            UserFile userFile = fileStorageService.storeFile(file, user);

            FileResponse response = FileResponse.builder()
                    .id(userFile.getId())
                    .fileName(userFile.getFileName())
                    .originalName(userFile.getOriginalName())
                    .fileSize(userFile.getFileSize())
                    .fileType(userFile.getFileType())
                    .mimeType(userFile.getMimeType())
                    .s3Url(userFile.getS3Url())
                    .isConverted(false)
                    .uploadedAt(userFile.getUploadedAt().toString())
                    .build();

            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to upload file: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<ApiResponse<List<FileResponse>>> uploadMultipleFiles(
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            List<FileResponse> responses = new java.util.ArrayList<>();

            for (MultipartFile file : files) {
                UserFile userFile = fileStorageService.storeFile(file, user);
                responses.add(FileResponse.builder()
                        .id(userFile.getId())
                        .fileName(userFile.getFileName())
                        .originalName(userFile.getOriginalName())
                        .fileSize(userFile.getFileSize())
                        .fileType(userFile.getFileType())
                        .mimeType(userFile.getMimeType())
                        .s3Url(userFile.getS3Url())
                        .isConverted(false)
                        .uploadedAt(userFile.getUploadedAt().toString())
                        .build());
            }

            return ResponseEntity.ok(ApiResponse.success("Files uploaded successfully", responses));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to upload files: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FileResponse>>> getUserFiles(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        List<FileResponse> files = fileStorageService.getUserFiles(user);
        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        UserFile userFile = fileStorageService.getFileById(fileId);

        if (!userFile.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        Resource resource = fileStorageService.loadFileAsResource(fileId, user);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(userFile.getMimeType() != null ? 
                    userFile.getMimeType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + userFile.getOriginalName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable Long fileId,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            fileStorageService.deleteFile(fileId, user);
            return ResponseEntity.ok(ApiResponse.success("File deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to delete file: " + e.getMessage()));
        }
    }

    @PostMapping("/share")
    public ResponseEntity<ApiResponse<ShareResponse>> shareFile(
            @RequestBody ShareRequest request,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
            ShareResponse response = fileStorageService.shareFile(request.getFileId(), user, 
                request.getExpirationHours(), baseUrl);
            return ResponseEntity.ok(ApiResponse.success("File shared successfully", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to share file: " + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getStats(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        DashboardStats stats = fileStorageService.getDashboardStats(user);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
