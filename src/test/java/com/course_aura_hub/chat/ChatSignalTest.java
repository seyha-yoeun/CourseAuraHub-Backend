package com.course_aura_hub.chat;

import com.course_aura_hub.chat.service.ChatSignal;
import com.course_aura_hub.common.cluster.ClusterMessageBus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSignalTest {

    @Test
    void signals_surviveTheTripBetweenInstances() {
        UUID actor = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ChatSignal signal = new ChatSignal(ChatSignal.Kind.MEMBER_LEFT, 12, 7, actor, target, 99);
        assertThat(ChatSignal.decode(signal.encode())).isEqualTo(signal);

        ChatSignal sparse = ChatSignal.conversation(ChatSignal.Kind.TYPING, 3, actor, null);
        assertThat(ChatSignal.decode(sparse.encode())).isEqualTo(sparse);

        ChatSignal presence = ChatSignal.presence(actor);
        assertThat(ChatSignal.decode(presence.encode())).isEqualTo(presence);
    }

    @Test
    void malformedBodies_areIgnored() {
        assertThat(ChatSignal.decode(null)).isNull();
        assertThat(ChatSignal.decode("TYPING,1")).isNull();
        assertThat(ChatSignal.decode("NOPE,1,,,,")).isNull();
        assertThat(ChatSignal.decode("TYPING,x,,,,")).isNull();
    }

    @Test
    void theBus_dropsItsOwnMessagesAndMalformedOnes() {
        assertThat(ClusterMessageBus.bodyFromOtherNode("node-b|MESSAGE_CREATED,1,2,,,", "node-a")).isEqualTo("MESSAGE_CREATED,1,2,,,");
        assertThat(ClusterMessageBus.bodyFromOtherNode("node-a|anything", "node-a")).isNull();
        assertThat(ClusterMessageBus.bodyFromOtherNode("no-separator", "node-a")).isNull();
        assertThat(ClusterMessageBus.bodyFromOtherNode(null, "node-a")).isNull();
    }
}
