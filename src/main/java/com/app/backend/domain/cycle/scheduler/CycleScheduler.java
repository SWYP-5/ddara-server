package com.app.backend.domain.cycle.scheduler;

import com.app.backend.domain.cycle.service.CycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(CycleScheduler.class);

    private final CycleService cycleService;

    public CycleScheduler(CycleService cycleService) {
        this.cycleService = cycleService;
    }

    // 1분마다 deadline(24h) 지난 진행 중 회차를 자동 마감
    @Scheduled(fixedDelay = 60_000)
    public void closeOverdueCycles() {
        int closed = cycleService.closeOverdueCycles();
        if (closed > 0) {
            log.info("24h 경과 회차 {}건 자동 마감", closed);
        }
    }

    // 1분마다 마감 1시간 내 회차의 미참여 멤버에게 임박 알림 (#77)
    // 같은 회차 중복 발송은 NotificationService가 막으므로 매분 돌아도 안전
    @Scheduled(fixedDelay = 60_000)
    public void notifyUpcomingDeadlines() {
        cycleService.notifyUpcomingDeadlines();
    }
}