package com.docconvert.controller;

import com.docconvert.dto.FileDTOs.ApiResponse;
import com.docconvert.dto.MetadataDTOs.*;
import com.docconvert.entity.User;
import com.docconvert.service.MetadataService;
import com.docconvert.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Metadata", description = "File metadata extraction endpoints")
public class MetadataController {

    private final MetadataService metadataService;
    private final UserService userService;

    @GetMapping("/{fileId}")
    @Operation(summary = "Extract metadata from a file",
            description = "Extracts comprehensive metadata from PDF, Word, Excel, PowerPoint, and image files")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Metadata extracted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "File not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<ApiResponse<FileMetadata>> getFileMetadata(
            @Parameter(description = "ID of the file") @PathVariable Long fileId,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            FileMetadata metadata = metadataService.extractMetadata(fileId, user);
            return ResponseEntity.ok(ApiResponse.success("Metadata extracted successfully", metadata));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to extract metadata: " + e.getMessage()));
        }
    }

    @PostMapping("/batch")
    @Operation(summary = "Extract metadata from multiple files",
            description = "Extracts metadata from multiple files in a single request")
    public ResponseEntity<ApiResponse<BatchMetadataResponse>> getBatchMetadata(
            @RequestBody List<Long> fileIds,
            Authentication authentication) {
        try {
            User user = userService.getUserByUsername(authentication.getName());
            List<FileMetadata> metadataList = metadataService.extractMetadataForMultipleFiles(fileIds, user);

            BatchMetadataResponse response = BatchMetadataResponse.builder()
                    .files(metadataList)
                    .totalFiles(fileIds.size())
                    .successCount(metadataList.size())
                    .failureCount(fileIds.size() - metadataList.size())
                    .build();

            return ResponseEntity.ok(ApiResponse.success("Batch metadata extraction completed", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to extract batch metadata: " + e.getMessage()));
        }
    }
}