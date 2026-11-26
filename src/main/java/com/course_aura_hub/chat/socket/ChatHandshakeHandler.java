package com.course_aura_hub.chat.socket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/** Makes the ticket's user the session principal; there is no HTTP session or cookie authentication here. */
public class ChatHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            @NonNull ServerHttpRequest request,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        Object userId = attributes.get(ChatHandshakeInterceptor.USER_ATTRIBUTE);
        return userId instanceof UUID id ? new ChatPrincipal(id) : null;
    }
}
