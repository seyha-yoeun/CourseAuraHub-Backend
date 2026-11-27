package com.course_aura_hub.chat.socket;

import java.security.Principal;
import java.util.UUID;

/** The user behind a chat WebSocket session; its name is the user id, which user destinations route on. */
public record ChatPrincipal(UUID userId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
