package com.app.backend.domain.block.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.app.backend.global.util.KstTime;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class DiscordBlockNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordBlockNotifier.class);

    private final RestClient restClient;
    private final String webhookUrl;

    public DiscordBlockNotifier(RestClient.Builder builder,
                                @Value("${discord.block-webhook-url:}") String webhookUrl) {
        this.restClient = builder.build();
        this.webhookUrl = webhookUrl;
    }

    public void notify(Long blockerId, String blockerNickname, Long blockedId, String blockedNickname,
                       Long groupId, String groupName,
                       LocalDateTime blockedAt, String recentImageUrl, String recentComment) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("차단 웹훅 URL 미설정 — 통지 생략: blocker={}, blocked={}", blockerId, blockedId);
            return;
        }
        StringBuilder content = new StringBuilder()
                .append("🚫 유저 차단\n")
                .append("차단자: ").append(blockerNickname).append(" (id ").append(blockerId).append(")\n")
                .append("피차단자: ").append(blockedNickname).append(" (id ").append(blockedId).append(")\n")
                .append("모임: ").append(groupName).append(" (id ").append(groupId).append(")\n")
                .append("시각: ").append(KstTime.format(blockedAt));
        if (recentImageUrl != null) {
            content.append("\n피차단자 최근 사진: ").append(recentImageUrl);
        }
        if (recentComment != null) {
            content.append("\n피차단자 최근 댓글: ").append(recentComment);
        }
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content.toString()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("차단 디스코드 통지 실패: blocker={}, blocked={}", blockerId, blockedId, e);
        }
    }
}