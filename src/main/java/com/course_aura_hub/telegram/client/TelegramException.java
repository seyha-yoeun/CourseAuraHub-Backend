package com.course_aura_hub.telegram.client;

import lombok.Getter;

@Getter
public class TelegramException extends RuntimeException {

    /** Telegram refused the chat for good, usually because the person blocked the bot. */
    private final boolean chatUnavailable;

    public TelegramException(String message, boolean chatUnavailable) {
        super(message);
        this.chatUnavailable = chatUnavailable;
    }
}
