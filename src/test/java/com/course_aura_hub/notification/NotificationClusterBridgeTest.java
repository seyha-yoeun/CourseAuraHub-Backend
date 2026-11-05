package com.course_aura_hub.notification;

import org.junit.jupiter.api.Test;

import static com.course_aura_hub.notification.service.NotificationClusterBridgeAccess.parseRemote;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationClusterBridgeTest {

    @Test
    void readsIdsFromOtherInstancesOnly() {
        assertThat(parseRemote("node-b|1,2,3", "node-a")).containsExactly(1L, 2L, 3L);
        assertThat(parseRemote("node-a|1,2", "node-a")).isEmpty();
    }

    @Test
    void ignoresMalformedPayloads() {
        assertThat(parseRemote("no-separator", "node-a")).isEmpty();
        assertThat(parseRemote("|1", "node-a")).isEmpty();
        assertThat(parseRemote("node-b|1,x", "node-a")).isEmpty();
        assertThat(parseRemote(null, "node-a")).isEmpty();
    }
}
