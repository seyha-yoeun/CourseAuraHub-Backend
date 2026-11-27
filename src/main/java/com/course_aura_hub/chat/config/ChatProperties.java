package com.course_aura_hub.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Chat limits and real-time transport settings.
 *
 * @param socketSessionLifetime   a WebSocket session is closed after this long; the client gets a new ticket and
 *                                reconnects, which re-checks the account
 * @param heartbeat               STOMP heartbeat interval in both directions
 * @param maxFrameBytes           largest inbound STOMP frame
 * @param clusterFanout           relay live chat events to other instances over Postgres LISTEN/NOTIFY
 * @param groupMemberLimit        most active members a group may have
 * @param inviteTtl               how long a group invite stays open
 * @param deleteWindow            how long after sending people may delete their own message
 * @param editWindow              how long after sending people may edit their own message
 * @param maxAttachments          most files on one message
 * @param maxPins                 most messages pinned in one conversation
 * @param maxPinnedConversations  most conversations one person may keep at the top of their list
 * @param messagesPerMinute       messages one user may send per minute
 * @param reactionsPerMinute      reactions one user may add or change per minute
 * @param directsPerDay           new direct conversations one user may start per day
 * @param groupsPerDay            groups one user may create per day
 * @param invitesPerDay           group invites one user may send per day
 * @param directoryQueriesPerMinute course member searches one user may run per minute
 * @param maintenanceCron         when the group clean-up runs (expired invites, members who lost access)
 * @param presenceHeartbeat       how often each instance refreshes the presence of the users connected to it
 * @param presenceGrace           how long after someone's last session closes before they show as offline
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        @DefaultValue("15m") Duration socketSessionLifetime,
        @DefaultValue("10s") Duration heartbeat,
        @DefaultValue("65536") int maxFrameBytes,
        @DefaultValue("true") boolean clusterFanout,
        @DefaultValue("100") int groupMemberLimit,
        @DefaultValue("7d") Duration inviteTtl,
        @DefaultValue("15m") Duration deleteWindow,
        @DefaultValue("15m") Duration editWindow,
        @DefaultValue("5") int maxAttachments,
        @DefaultValue("5") int maxPins,
        @DefaultValue("5") int maxPinnedConversations,
        @DefaultValue("30") int messagesPerMinute,
        @DefaultValue("60") int reactionsPerMinute,
        @DefaultValue("20") int directsPerDay,
        @DefaultValue("5") int groupsPerDay,
        @DefaultValue("50") int invitesPerDay,
        @DefaultValue("60") int directoryQueriesPerMinute,
        @DefaultValue("0 7 * * * *") String maintenanceCron,
        @DefaultValue("PT30S") Duration presenceHeartbeat,
        @DefaultValue("PT20S") Duration presenceGrace
) {
}
