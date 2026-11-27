package com.course_aura_hub.chat.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hourly clean-up on one instance at a time: groups ({@link ChatGroupService#runMaintenance()}) and presence rows
 * left by stopped instances ({@link ChatPresence#prune()}).
 */
@Component
@RequiredArgsConstructor
public class ChatMaintenanceJob {

    private final ChatGroupService groupService;
    private final ChatPresence presence;
    private final ClusterJobLock jobLock;

    @Scheduled(cron = "${app.chat.maintenance-cron:0 7 * * * *}")
    public void run() {
        jobLock.runExclusively("chat-group-maintenance", Duration.ofMinutes(30), Duration.ofMinutes(1), groupService::runMaintenance);
        jobLock.runExclusively("chat-presence-prune", Duration.ofMinutes(5), Duration.ofMinutes(1), presence::prune);
    }
}
