package com.course_aura_hub.chat.socket;

import com.course_aura_hub.auth.config.CorsProperties;
import com.course_aura_hub.chat.service.ChatSocketTickets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates the chat WebSocket handshake with a single-use ticket ({@code ?ticket=}). A browser's Origin must
 * be one of the trusted frontend origins; this is checked before the ticket is spent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ATTRIBUTE = "chat.userId";

    private final ChatSocketTickets tickets;
    private final CorsProperties corsProperties;

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin != null && !corsProperties.allowedOrigins().contains(origin)) {
            log.warn("Chat handshake refused for origin {}", origin);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        String ticket = request instanceof ServletServerHttpRequest servlet
                ? servlet.getServletRequest().getParameter("ticket") : null;
        Optional<UUID> userId = tickets.redeem(ticket);
        if (userId.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ATTRIBUTE, userId.get());
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception
    ) {
        // Nothing to clean up
    }
}
