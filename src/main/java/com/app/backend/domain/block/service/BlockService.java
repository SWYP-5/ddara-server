package com.app.backend.domain.block.service;

import com.app.backend.domain.block.dto.BlockListResponse;
import com.app.backend.domain.block.entity.Block;
import com.app.backend.domain.block.repository.BlockRepository;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlockService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    public BlockService(BlockRepository blockRepository, UserRepository userRepository) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void block(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId)) {
            return;
        }
        blockRepository.save(Block.builder()
                .blockerId(userId)
                .blockedId(targetUserId)
                .build());
    }

    @Transactional
    public void unblock(Long userId, Long targetUserId) {
        blockRepository.deleteByBlockerIdAndBlockedId(userId, targetUserId);
    }

    @Transactional(readOnly = true)
    public BlockListResponse getBlocks(Long userId) {
        List<Block> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(userId);
        Map<Long, User> usersById = userRepository.findAllById(
                        blocks.stream().map(Block::getBlockedId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<BlockListResponse.BlockedUser> items = blocks.stream()
                .map(block -> new BlockListResponse.BlockedUser(
                        block.getBlockedId(),
                        usersById.containsKey(block.getBlockedId())
                                ? usersById.get(block.getBlockedId()).getName() : "탈퇴한사용자",
                        block.getCreatedAt().atZone(SEOUL).toOffsetDateTime()))
                .toList();
        return new BlockListResponse(items);
    }
}