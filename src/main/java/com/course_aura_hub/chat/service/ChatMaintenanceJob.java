package com.course_aura_hub.chat.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Hourly group clean-up on one instance at a time; the work is in {@link ChatGroupService#runMaintenance()}. */
@Component
@RequiredArgsConstructor
public class ChatMaintenanceJob {

    private final ChatGroupService groupService;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.chat.maintenance-cron:0 7 * * * *}")
    public void run() {
        jobLock.runExclusively("chat-group-maintenance", Duration.ofMinutes(30), Duration.ofMinutes(1), groupService::runMaintenance);
    }
}
