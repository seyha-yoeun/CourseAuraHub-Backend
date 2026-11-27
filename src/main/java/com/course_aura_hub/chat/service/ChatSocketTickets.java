package com.course_aura_hub.chat.service;

import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.model.OneTimeToken;
import com.course_aura_hub.auth.model.Role;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.service.OneTimeTokenService;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.exception.ForbiddenActionException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Single-use tickets for the chat WebSocket. Browsers can't send our auth cookies to the backend origin, so the
 * client asks for a ticket through the authenticated REST API and passes it once on the handshake. A ticket lives
 * 60 seconds; asking for a new one cancels an unused one.
 */
@Component
@RequiredArgsConstructor
public class ChatSocketTickets {

    public static final String SOCKET_PATH = "/ws/chat";

    private final OneTimeTokenService tokenService;
    private final UserRepository userRepository;

    @Transactional
    public ChatDtos.SocketTicket issue(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        OneTimeTokenService.IssuedToken issued = tokenService.issue(user, TokenPurpose.CHAT_SOCKET, false);
        return new ChatDtos.SocketTicket(issued.rawValue(), SOCKET_PATH, issued.token().getExpiresAt());
    }

    /** The owner of a valid, unused ticket, consuming it; empty for anything else. */
    @Transactional
    public Optional<UUID> redeem(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank() || rawTicket.length() > 200) {
            return Optional.empty();
        }
        try {
            OneTimeToken token = tokenService.lockUsableOpaqueToken(TokenPurpose.CHAT_SOCKET, rawTicket, "Invalid ticket");
            tokenService.consume(token);
            User user = token.getUser();
            return user.isEnabled() ? Optional.of(user.getId()) : Optional.empty();
        } catch (InvalidOneTimeCodeException ex) {
            return Optional.empty();
        }
    }

    /** The acting user behind a socket session, re-read so disabled accounts and role changes apply at once. */
    @Transactional(readOnly = true)
    public Actor actor(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new ForbiddenActionException("Your session has ended. Sign in again."));
        return new Actor(user.getId(), user.getEmail(), user.getRole() == Role.ROLE_ADMIN);
    }
}
