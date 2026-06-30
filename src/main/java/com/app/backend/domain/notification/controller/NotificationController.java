package com.app.backend.domain.notification.controller;

import com.app.backend.domain.notification.dto.NotificationListResponse;
import com.app.backend.domain.notification.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
