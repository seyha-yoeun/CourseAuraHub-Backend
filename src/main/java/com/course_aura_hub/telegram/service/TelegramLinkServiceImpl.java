package com.course_aura_hub.telegram.service;

import com.course_aura_hub.auth.exception.InvalidOneTimeCodeException;
import com.course_aura_hub.auth.model.OneTimeToken;
import com.course_aura_hub.auth.model.TokenPurpose;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.service.OneTimeTokenService;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.dto.TelegramConnectLinkResponse;
import com.course_aura_hub.telegram.dto.TelegramStatusResponse;
import com.course_aura_hub.telegram.model.TelegramLink;
import com.course_aura_hub.telegram.repository.TelegramLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkServiceImpl implements TelegramLinkService {

    private final TelegramLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final OneTimeTokenService oneTimeTokenService;
    private final TelegramProperties properties;

    @Override
    @Transactional(readOnly = true)
    public TelegramStatusResponse status(UUID userId) {
        return linkRepository.findById(userId)
                .map(this::toStatus)
                .orElseGet(() -> new TelegramStatusResponse(properties.isConfigured(), false, false, null, null));
    }

    @Override
    @Transactional
    public TelegramConnectLinkResponse createConnectLink(UUID userId) {
        if (!properties.isConfigured()) {
            throw new ServiceUnavailableException("Telegram is not available yet");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        OneTimeTokenService.IssuedToken issued = oneTimeTokenService.issue(user, TokenPurpose.TELEGRAM_LINK, false);
        return new TelegramConnectLinkResponse(
                "https://t.me/" + properties.botUsername() + "?start=" + issued.rawValue(),
                issued.token().getExpiresAt()
        );
    }

    @Override
    @Transactional
    public Optional<User> connect(String rawToken, long chatId, String telegramName) {
        Optional<UUID> ownerId = oneTimeTokenService.findOwnerOfOpaqueToken(TokenPurpose.TELEGRAM_LINK, rawToken);
        if (ownerId.isEmpty()) {
            return Optional.empty();
        }
        User user = userRepository.findByIdForUpdate(ownerId.get()).orElse(null);
        if (user == null || !user.isEnabled()) {
            return Optional.empty();
        }

        OneTimeToken token;
        try {
            token = oneTimeTokenService.lockUsableOpaqueToken(TokenPurpose.TELEGRAM_LINK, rawToken, "Invalid link");
        } catch (InvalidOneTimeCodeException ex) {
            return Optional.empty();
        }
        oneTimeTokenService.consume(token);

        linkRepository.deleteByChatId(chatId);
        linkRepository.flush();
        TelegramLink link = linkRepository.findById(user.getId())
                .orElseGet(() -> TelegramLink.builder().userId(user.getId()).build());
        link.setChatId(chatId);
        link.setTelegramName(telegramName);
        link.setEnabled(true);
        link.setLinkedAt(Instant.now());
        linkRepository.save(link);

        log.info("Connected Telegram chat userId={}", user.getId());
        return Optional.of(user);
    }

    @Override
    @Transactional
    public TelegramStatusResponse setEnabled(UUID userId, boolean enabled) {
        TelegramLink link = linkRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("Connect Telegram first"));
        link.setEnabled(enabled);
        return toStatus(link);
    }

    @Override
    @Transactional
    public void disconnect(UUID userId) {
        linkRepository.findById(userId).ifPresent(linkRepository::delete);
    }

    @Override
    @Transactional
    public void disconnectChat(long chatId) {
        linkRepository.deleteByChatId(chatId);
    }

    private TelegramStatusResponse toStatus(TelegramLink link) {
        return new TelegramStatusResponse(
                properties.isConfigured(), true, link.isEnabled(), link.getTelegramName(), link.getLinkedAt());
    }
}
