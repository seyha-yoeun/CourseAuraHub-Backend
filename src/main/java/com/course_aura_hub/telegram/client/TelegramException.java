package com.course_aura_hub.telegram.client;

import lombok.Getter;

@Getter
public class TelegramException extends RuntimeException {

    /** Telegram refused the chat for good, usually because the person blocked the bot. */
    private final boolean chatUnavailable;

    /** The HTTP status Telegram answered with, or 0 when the request got no answer. */
    private final int statusCode;

    public TelegramException(String message, boolean chatUnavailable) {
        this(message, chatUnavailable, 0);
    }

    public TelegramException(String message, boolean chatUnavailable, int statusCode) {
        super(message);
        this.chatUnavailable = chatUnavailable;
        this.statusCode = statusCode;
    }
}
