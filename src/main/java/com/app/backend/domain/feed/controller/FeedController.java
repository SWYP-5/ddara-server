package com.app.backend.domain.feed.controller;

import com.app.backend.domain.feed.dto.FeedResponse;
import com.app.backend.domain.feed.service.FeedService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    // 최근 업데이트 피드 (FEED-01)
    @GetMapping("/api/feed")
    public FeedResponse getFeed(@AuthenticationPrincipal Long userId,
                                @RequestParam(defaultValue = "30") int size) {
        return feedService.getFeed(userId, size);
    }
}