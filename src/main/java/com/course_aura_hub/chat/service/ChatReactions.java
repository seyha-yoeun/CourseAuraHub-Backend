package com.course_aura_hub.chat.service;

import java.util.List;

/**
 * The emoji people can react to a chat message with. The web app offers the same set
 * ({@code app/utils/chatEmoji.ts}), so keep the two in step.
 */
public final class ChatReactions {

    /** 👍 ❤️ 😂 😮 😢 🎉 */
    public static final List<String> ALLOWED = List.of(
            "👍",
            "❤️",
            "😂",
            "😮",
            "😢",
            "🎉");

    private static final String HEART_WITHOUT_VARIATION = "❤";

    private ChatReactions() {
    }

    /** The allowed emoji the input stands for, or null when it isn't one of them. */
    public static String normalize(String emoji) {
        if (emoji == null) {
            return null;
        }
        String value = emoji.strip();
        // Some keyboards send the heart without its emoji variation selector
        if (HEART_WITHOUT_VARIATION.equals(value)) {
            return ALLOWED.get(1);
        }
        return ALLOWED.contains(value) ? value : null;
    }
}
