package com.app.backend.domain.notification.controller;

import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 알림 목록 조회 (N-01)
    @GetMapping
    public NotificationListResponse getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "30") int size) {
        return notificationService.getNotifications(userId, category, size);
    }

    // 알림 1건 읽음 (N-02) — 멱등 204
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(userId, notificationId);
    }

    // 알림 전체 읽음 (N-03) — 멱등 204
    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllAsRead(userId);
    }
}
