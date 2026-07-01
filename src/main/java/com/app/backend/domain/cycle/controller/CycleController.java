package com.app.backend.domain.cycle.controller;

import com.app.backend.domain.cycle.dto.CreateCycleRequest;
import com.app.backend.domain.cycle.dto.CurrentCycleResponse;
import com.app.backend.domain.cycle.dto.CycleCreateResponse;
import com.app.backend.domain.cycle.dto.PastCyclesResponse;
import com.app.backend.domain.cycle.service.CycleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/cycles")
public class CycleController {

    private final CycleService cycleService;

    public CycleController(CycleService cycleService) {
        this.cycleService = cycleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CycleCreateResponse createCycle(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long groupId,
                                           @Valid @RequestBody CreateCycleRequest request) {
        return cycleService.createCycle(userId, groupId, request);
    }

    @GetMapping("/current")
    public CurrentCycleResponse getCurrentCycle(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long groupId) {
        return cycleService.getCurrentCycle(userId, groupId);
    }
    
    @GetMapping
    public PastCyclesResponse getPastCycles(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long groupId,
                                            @RequestParam String status) {
        return cycleService.getPastCycles(userId, groupId);
    }
}