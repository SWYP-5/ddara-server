package com.app.backend.domain.shot.controller;

import com.app.backend.domain.shot.dto.ShotResponse;
import com.app.backend.domain.shot.dto.ShotUploadRequest;
import com.app.backend.domain.shot.service.ShotService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cycles/{cycleId}/shots")
public class ShotController {

    private final ShotService shotService;

    public ShotController(ShotService shotService) {
        this.shotService = shotService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShotResponse uploadShot(@AuthenticationPrincipal Long userId,
                                   @PathVariable Long cycleId,
                                   @Valid @RequestBody ShotUploadRequest request) {
        return shotService.uploadShot(userId, cycleId, request);
    }
}
