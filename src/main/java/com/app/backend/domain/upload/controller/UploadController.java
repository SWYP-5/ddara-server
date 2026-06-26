package com.app.backend.domain.upload.controller;

import com.app.backend.domain.upload.dto.PresignRequest;
import com.app.backend.domain.upload.dto.PresignResponse;
import com.app.backend.domain.upload.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }
    
    @PostMapping("/presign")
    public PresignResponse presign(@Valid @RequestBody PresignRequest request) {
        return uploadService.createPresignedUrl(request);
    }
}