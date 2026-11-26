package com.course_aura_hub.chat.socket;

import com.course_aura_hub.chat.service.ChatDispatcher;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Inbound STOMP rules: every frame needs the handshake's principal, clients may only subscribe to their own
 * chat queue, and may only send chat commands.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    static final String SUBSCRIPTION = "/user" + ChatDispatcher.USER_QUEUE;
    static final String COMMAND_PREFIX = "/app/chat.";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (!(accessor.getUser() instanceof ChatPrincipal)) {
            throw new MessageDeliveryException("Not authenticated");
        }
        if (command == StompCommand.SUBSCRIBE && !SUBSCRIPTION.equals(accessor.getDestination())) {
            throw new MessageDeliveryException("You can only subscribe to " + SUBSCRIPTION);
        }
        if (command == StompCommand.SEND
                && (accessor.getDestination() == null || !accessor.getDestination().startsWith(COMMAND_PREFIX))) {
            throw new MessageDeliveryException("Unknown destination");
        }
        return message;
    }
}
