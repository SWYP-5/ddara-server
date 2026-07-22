package com.app.backend.domain.block.service;

import com.app.backend.domain.block.client.DiscordBlockNotifier;
import com.app.backend.domain.block.dto.BlockListResponse;
import com.app.backend.domain.block.entity.Block;
import com.app.backend.domain.block.repository.BlockRepository;
import com.app.backend.domain.comment.entity.Comment;
import com.app.backend.domain.comment.repository.CommentRepository;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.domain.user.entity.User;
import com.app.backend.domain.user.repository.UserRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlockService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final CycleRepository cycleRepository;
    private final ShotRepository shotRepository;
    private final CommentRepository commentRepository;
    private final DiscordBlockNotifier discordBlockNotifier;

    public BlockService(BlockRepository blockRepository,
                        UserRepository userRepository,
                        GroupRepository groupRepository,
                        MembershipRepository membershipRepository,
                        CycleRepository cycleRepository,
                        ShotRepository shotRepository,
                        CommentRepository commentRepository,
                        DiscordBlockNotifier discordBlockNotifier) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.cycleRepository = cycleRepository;
        this.shotRepository = shotRepository;
        this.commentRepository = commentRepository;
        this.discordBlockNotifier = discordBlockNotifier;
    }

    @Transactional
    public void block(Long userId, Long targetUserId, Long groupId) {
        if (userId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId)) {
            return;
        }
        Block block = blockRepository.save(Block.builder()
                .blockerId(userId)
                .blockedId(targetUserId)
                .build());

        notifyBlock(userId, targetUserId, groupId, block.getCreatedAt());
    }

    // 차단 등록 성공 시 디스코드 웹훅 통지
    private void notifyBlock(Long blockerId, Long blockedId, Long groupId, LocalDateTime blockedAt) {
        String groupName = groupRepository.findById(groupId).map(Group::getName).orElse("(알 수 없음)");
        String blockerNickname = groupNickname(groupId, blockerId);
        String blockedNickname = groupNickname(groupId, blockedId);

        List<Long> cycleIds = cycleRepository.findByGroupId(groupId).stream()
                .map(Cycle::getId).toList();
        List<Shot> groupShots = cycleIds.isEmpty() ? List.of()
                : shotRepository.findByCycleIdIn(cycleIds).stream()
                .filter(s -> s.getDeletedAt() == null && !s.isRemoved())
                .toList();

        String recentImageUrl = groupShots.stream()
                .filter(s -> s.getUserId().equals(blockedId))
                .max(Comparator.comparing(Shot::getUploadedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(Shot::getImageUrl)
                .orElse(null);

        List<Long> groupShotIds = groupShots.stream().map(Shot::getId).toList();
        String recentComment = groupShotIds.isEmpty() ? null
                : commentRepository.findByShotIdInAndDeletedAtIsNull(groupShotIds).stream()
                .filter(c -> c.getUserId().equals(blockedId) && !c.isRemoved())
                .max(Comparator.comparing(Comment::getCreatedAt))
                .map(Comment::getContent)
                .orElse(null);

        discordBlockNotifier.notify(blockerId, blockerNickname, blockedId, blockedNickname,
                groupId, groupName, blockedAt, recentImageUrl, recentComment);
    }

    private String groupNickname(Long groupId, Long userId) {
        return membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .map(Membership::getNickname)
                .orElse("(알 수 없음)");
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