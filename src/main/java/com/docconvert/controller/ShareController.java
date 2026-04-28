package com.docconvert.controller;

import com.docconvert.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ShareController {

    private final FileStorageService fileStorageService;

    @GetMapping("/public/{shareToken}")
    public ResponseEntity<Resource> downloadSharedFile(@PathVariable String shareToken) {
        try {
            Resource resource = fileStorageService.loadSharedFile(shareToken);
            String fileName = fileStorageService.getSharedFileName(shareToken);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/public/{shareToken}/info")
    public ResponseEntity<?> getSharedFileInfo(@PathVariable String shareToken) {
        try {
            String fileName = fileStorageService.getSharedFileName(shareToken);
            return ResponseEntity.ok().body(java.util.Map.of(
                "fileName", fileName,
                "shareToken", shareToken
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
