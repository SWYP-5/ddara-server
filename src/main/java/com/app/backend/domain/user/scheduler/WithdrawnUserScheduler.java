package com.app.backend.domain.user.scheduler;

import com.app.backend.domain.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 후 보존기간(5일)이 지난 사용자를 완전 삭제하는 스케줄러. (U-05)
 * 실제 삭제 로직은 {@link UserService#purgeWithdrawnUsers()}에 있다.
 */
@Component
public class WithdrawnUserScheduler {

    private static final Logger log = LoggerFactory.getLogger(WithdrawnUserScheduler.class);

    private final UserService userService;

    public WithdrawnUserScheduler(UserService userService) {
        this.userService = userService;
    }

    // 매일 새벽 4시(KST): 보존기간 지난 탈퇴 사용자 완전 삭제
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void purgeWithdrawnUsers() {
        userService.purgeWithdrawnUsers();
        log.info("보존기간 지난 탈퇴 사용자 완전 삭제 배치 실행");
    }
}
