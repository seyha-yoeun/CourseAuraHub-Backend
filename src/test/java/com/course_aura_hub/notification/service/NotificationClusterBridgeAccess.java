package com.course_aura_hub.notification.service;

import java.util.List;

/** Exposes the bridge's package-private payload parser to tests in other packages. */
public final class NotificationClusterBridgeAccess {

    private NotificationClusterBridgeAccess() {
    }

    public static List<Long> parseRemote(String payload, String ownNodeId) {
        return NotificationClusterBridge.parseRemote(payload, ownNodeId);
    }
}
