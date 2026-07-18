package com.app.backend.domain.feed.service;

import com.app.backend.domain.comment.entity.Comment;
import com.app.backend.domain.comment.repository.CommentRepository;
import com.app.backend.domain.cycle.entity.Cycle;
import com.app.backend.domain.cycle.entity.CycleStatus;
import com.app.backend.domain.cycle.repository.CycleRepository;
import com.app.backend.domain.feed.dto.FeedResponse;
import com.app.backend.domain.group.entity.Group;
import com.app.backend.domain.group.entity.Membership;
import com.app.backend.domain.group.repository.GroupRepository;
import com.app.backend.domain.group.repository.MembershipRepository;
import com.app.backend.domain.shot.entity.Shot;
import com.app.backend.domain.shot.entity.ShotType;
import com.app.backend.domain.shot.repository.ShotRepository;
import com.app.backend.global.exception.CustomException;
import com.app.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final CycleRepository cycleRepository;
    private final ShotRepository shotRepository;
    private final CommentRepository commentRepository;

    public FeedService(MembershipRepository membershipRepository,
                       GroupRepository groupRepository,
                       CycleRepository cycleRepository,
                       ShotRepository shotRepository,
                       CommentRepository commentRepository) {
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.cycleRepository = cycleRepository;
        this.shotRepository = shotRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional(readOnly = true)
    public FeedResponse getFeed(Long userId, int size) {
        if (size < 1) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        List<Membership> memberships = membershipRepository.findByUserIdAndLeftAtIsNull(userId);
        if (memberships.isEmpty()) {
            return new FeedResponse(0, List.of());
        }

        Map<Long, Group> groupsById = groupRepository.findAllById(
                        memberships.stream().map(Membership::getGroupId).toList()).stream()
                .filter(g -> g.getDeletedAt() == null)
                .collect(Collectors.toMap(Group::getId, Function.identity()));

        Map<Long, Cycle> cyclesById = groupsById.keySet().stream()
                .flatMap(groupId -> cycleRepository.findByGroupId(groupId).stream())
                .filter(c -> c.getStatus() != CycleStatus.REMOVED)
                .collect(Collectors.toMap(Cycle::getId, Function.identity()));
        if (cyclesById.isEmpty()) {
            return new FeedResponse(0, List.of());
        }

        List<Shot> allShots = shotRepository.findByCycleIdIn(List.copyOf(cyclesById.keySet())).stream()
                .filter(s -> s.getDeletedAt() == null && !s.isRemoved())
                .toList();

        Set<Long> myUploadedCycleIds = allShots.stream()
                .filter(s -> s.getUserId().equals(userId))
                .map(Shot::getCycleId)
                .collect(Collectors.toSet());

        List<Shot> friendShots = allShots.stream()
                .filter(s -> !s.getUserId().equals(userId))
                .sorted(Comparator.comparing(Shot::getUploadedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long updateCount = friendShots.stream()
                .filter(s -> cyclesById.get(s.getCycleId()).getStatus() == CycleStatus.IN_PROGRESS)
                .count();

        List<Shot> page = friendShots.stream().limit(size).toList();

        Map<Long, List<Comment>> commentsByShot = page.isEmpty() ? Map.of()
                : commentRepository.findByShotIdInAndDeletedAtIsNull(
                        page.stream().map(Shot::getId).toList()).stream()
                .filter(c -> !c.isRemoved())
                .collect(Collectors.groupingBy(Comment::getShotId));

        Map<String, String> nicknameCache = new HashMap<>();
        List<FeedResponse.FeedItem> items = page.stream()
                .map(shot -> {
                    Cycle cycle = cyclesById.get(shot.getCycleId());
                    Group group = groupsById.get(cycle.getGroupId());
                    boolean underReview = shot.isUnderReview();
                    boolean locked = cycle.getStatus() == CycleStatus.IN_PROGRESS
                            && shot.getType() == ShotType.MEMBER
                            && !myUploadedCycleIds.contains(cycle.getId());
                    List<Comment> comments = commentsByShot.getOrDefault(shot.getId(), List.of());
                    FeedResponse.LatestComment latestComment = comments.stream()
                            .max(Comparator.comparing(Comment::getCreatedAt))
                            .map(c -> new FeedResponse.LatestComment(
                                    c.getUserId(),
                                    nickname(nicknameCache, group.getId(), c.getUserId()),
                                    c.isUnderReview() ? null : c.getContent(),
                                    c.isUnderReview()))
                            .orElse(null);
                    return new FeedResponse.FeedItem(
                            shot.getId(),
                            shot.getType(),
                            underReview ? null : shot.getImageUrl(),
                            underReview,
                            shot.getUserId(),
                            nickname(nicknameCache, group.getId(), shot.getUserId()),
                            group.getId(),
                            group.getName(),
                            cycle.getId(),
                            cycle.getTopic(),
                            locked,
                            comments.size(),
                            latestComment,
                            toKstOffset(shot.getUploadedAt()));
                })
                .toList();

        return new FeedResponse(updateCount, items);
    }

    private String nickname(Map<String, String> cache, Long groupId, Long userId) {
        return cache.computeIfAbsent(groupId + ":" + userId, key ->
                membershipRepository.findByGroupIdAndUserId(groupId, userId)
                        .map(Membership::getNickname)
                        .orElse("탈퇴한사용자"));
    }

    private OffsetDateTime toKstOffset(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(SEOUL).toOffsetDateTime();
    }
}