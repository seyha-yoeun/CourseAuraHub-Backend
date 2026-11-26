package com.course_aura_hub.chat.socket;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChatChannelInterceptorTest {

    private final ChatChannelInterceptor interceptor = new ChatChannelInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void onlyTheOwnQueueAndChatCommands_areAllowed() {
        Principal user = new ChatPrincipal(UUID.randomUUID());
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/chat", user), channel))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", user), channel))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/queue/chat", user), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/" + UUID.randomUUID() + "/queue/chat", user), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/queue/chat", user), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void framesWithoutTheHandshakeUser_areRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", () -> "someone"), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
