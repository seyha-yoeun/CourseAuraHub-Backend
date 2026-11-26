package com.course_aura_hub.common.cluster;

/**
 * Receives messages another instance published on one Postgres NOTIFY channel. Implementations are Spring beans;
 * {@link ClusterMessageBus} listens on the channel of every enabled handler over one shared connection.
 */
public interface ClusterChannelHandler {

    /** The NOTIFY channel name: lowercase letters, digits and underscores only. */
    String channel();

    /** False turns the channel off on this instance: nothing is listened to or published. */
    boolean enabled();

    /** Called on the listener thread for messages from other instances only. Must not block for long. */
    void onMessage(String body);
}
