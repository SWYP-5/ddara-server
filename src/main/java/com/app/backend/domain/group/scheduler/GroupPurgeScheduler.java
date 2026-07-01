package com.app.backend.domain.group.scheduler;

import com.app.backend.domain.group.service.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GroupPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupPurgeScheduler.class);

    private final GroupService groupService;

    public GroupPurgeScheduler(GroupService groupService) {
        this.groupService = groupService;
    }

    // 매일 새벽 4시, 소프트 삭제된 지 5일 지난 모임을 완전 삭제
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeDeletedGroups() {
        int purged = groupService.purgeDeletedGroups();
        if (purged > 0) {
            log.info("보관기간(5일) 지난 모임 {}건 완전 삭제", purged);
        }
    }
}