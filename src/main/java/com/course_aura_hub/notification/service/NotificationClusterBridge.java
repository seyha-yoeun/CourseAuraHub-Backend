package com.course_aura_hub.notification.service;

import com.course_aura_hub.common.cluster.ClusterChannelHandler;
import com.course_aura_hub.common.cluster.ClusterMessageBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Carries notification pushes between instances, so a learner gets a live push whichever instance their stream
 * is open on. Messages hold only notification ids; receivers load the rows.
 *
 * <p>A lost message only costs a live push: the rows are saved and the REST feed and reconnecting streams
 * catch up. The connection is shared with other channels through {@link ClusterMessageBus}.</p>
 */
@Component
public class NotificationClusterBridge implements ClusterChannelHandler {

    static final String CHANNEL = "course_aura_notifications";
    private static final int IDS_PER_MESSAGE = 300;

    private final ClusterMessageBus bus;
    private final ApplicationEventPublisher events;
    private final boolean enabled;

    public NotificationClusterBridge(
            ClusterMessageBus bus,
            ApplicationEventPublisher events,
            @Value("${app.notifications.cluster-fanout:true}") boolean enabled
    ) {
        this.bus = bus;
        this.events = events;
        this.enabled = enabled;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    /** Tells the other instances about committed notifications. Call outside a transaction. */
    public void publish(List<Long> notificationIds) {
        if (!enabled || notificationIds.isEmpty()) {
            return;
        }
        for (int start = 0; start < notificationIds.size(); start += IDS_PER_MESSAGE) {
            List<Long> chunk = notificationIds.subList(start, Math.min(start + IDS_PER_MESSAGE, notificationIds.size()));
            bus.publish(CHANNEL, chunk.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
    }

    @Override
    public void onMessage(String body) {
        List<Long> ids = parseIds(body);
        if (!ids.isEmpty()) {
            events.publishEvent(new RemoteNotificationsReceived(ids));
        }
    }

    /** Ids from another instance's message; empty for this instance's own messages or a malformed payload. */
    static List<Long> parseRemote(String payload, String ownNodeId) {
        String body = ClusterMessageBus.bodyFromOtherNode(payload, ownNodeId);
        return body == null ? List.of() : parseIds(body);
    }

    private static List<Long> parseIds(String body) {
        List<Long> ids = new ArrayList<>();
        for (String part : body.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ex) {
                return List.of();
            }
        }
        return ids;
    }
}
