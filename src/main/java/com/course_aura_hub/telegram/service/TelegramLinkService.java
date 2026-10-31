package com.course_aura_hub.telegram.service;

import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.telegram.dto.TelegramConnectLinkResponse;
import com.course_aura_hub.telegram.dto.TelegramStatusResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * Connects accounts to Telegram chats. One account has one chat and one chat belongs to one account,
 * which is what keeps each person's notifications private.
 */
public interface TelegramLinkService {

    TelegramStatusResponse status(UUID userId);

    /** Issues a one-time connect link, replacing any earlier one. */
    TelegramConnectLinkResponse createConnectLink(UUID userId);

    /**
     * Redeems a connect token for a chat, taking the chat over if it was connected to another account.
     *
     * @return the account now connected, or empty when the token is invalid, expired or already used
     */
    Optional<User> connect(String rawToken, long chatId, String telegramName);

    TelegramStatusResponse setEnabled(UUID userId, boolean enabled);

    void disconnect(UUID userId);

    void disconnectChat(long chatId);
}
