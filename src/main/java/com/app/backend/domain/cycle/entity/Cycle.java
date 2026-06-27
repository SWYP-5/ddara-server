package com.app.backend.domain.cycle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "starter_cycles", indexes = {
        @Index(name = "uk_cycle_group_number", columnList = "group_id, cycle_number", unique = true),
        @Index(name = "idx_cycles_group_status", columnList = "group_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "starter_user_id", nullable = false)
    private Long starterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CycleStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    @Builder
    private Cycle(Long groupId, int cycleNumber, String topic, Long starterUserId,
                 LocalDateTime startedAt, LocalDateTime deadlineAt) {
        this.groupId = groupId;
        this.cycleNumber = cycleNumber;
        this.topic = topic;
        this.starterUserId = starterUserId;
        this.status = CycleStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
    }

    public void complete() {
        this.status = CycleStatus.DONE;
    }
}