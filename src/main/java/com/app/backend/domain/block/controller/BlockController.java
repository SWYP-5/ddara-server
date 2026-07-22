package com.app.backend.domain.block.controller;

import com.app.backend.domain.block.dto.BlockListResponse;
import com.app.backend.domain.block.dto.BlockRequest;
import com.app.backend.domain.block.service.BlockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    // 유저 차단 (BLOCK-01)
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Long userId,
                      @Valid @RequestBody BlockRequest request) {
        blockService.block(userId, request.userId(), request.groupId());
    }

    // 차단 해제 (BLOCK-02)
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Long userId,
                        @PathVariable("userId") Long targetUserId) {
        blockService.unblock(userId, targetUserId);
    }

    // 차단 목록 조회 (BLOCK-03)
    @GetMapping
    public BlockListResponse getBlocks(@AuthenticationPrincipal Long userId) {
        return blockService.getBlocks(userId);
    }
}