package com.course_aura_hub.chat.socket;

import com.course_aura_hub.chat.config.ChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Closes chat sessions after {@code app.chat.socket-session-lifetime}. The client then fetches a new ticket and
 * reconnects, which re-checks the account, so a disabled or signed-out user is cut off within that time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionRegistry implements WebSocketHandlerDecoratorFactory {

    /** Policy close code: tells the client to reconnect with a new ticket. */
    static final CloseStatus EXPIRED = new CloseStatus(4001, "Session expired");

    private final ChatProperties properties;
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    private record Entry(WebSocketSession session, Instant openedAt) {
    }

    @Override
    @NonNull
    public WebSocketHandler decorate(@NonNull WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
                sessions.put(session.getId(), new Entry(session, Instant.now()));
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    public int openSessions() {
        return sessions.size();
    }

    @Scheduled(fixedDelayString = "${app.chat.session-sweep-interval:PT30S}")
    public void closeExpired() {
        Instant cutoff = Instant.now().minus(properties.socketSessionLifetime());
        sessions.values().removeIf(entry -> {
            if (entry.openedAt().isAfter(cutoff)) {
                return false;
            }
            try {
                entry.session().close(EXPIRED);
            } catch (IOException ex) {
                log.debug("Could not close expired chat session {}", entry.session().getId(), ex);
            }
            return true;
        });
    }
}
