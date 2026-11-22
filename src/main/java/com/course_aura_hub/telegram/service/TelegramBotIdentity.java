package com.course_aura_hub.telegram.service;

import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The bot's username for {@code t.me} connect links. A link to anything but an existing username sends people to
 * telegram.org instead of the bot, so the configured value is tidied (a leading {@code @}, a {@code t.me} link and
 * spaces are dropped) and, at startup, checked against the bot the token belongs to. Telegram's answer wins.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotIdentity {

    // Telegram usernames: 5 to 32 letters, digits or underscores, starting with a letter; bot names end in "bot"
    private static final Pattern BOT_USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{3,31}");
    private static final Pattern LINK_PREFIX = Pattern.compile("^(?:https?://)?(?:www\\.)?(?:t|telegram)\\.me/", Pattern.CASE_INSENSITIVE);

    private final TelegramClient client;
    private final TelegramProperties properties;

    private volatile String verified;

    /** Asks Telegram which bot the token belongs to. Failures keep the configured name and are logged. */
    public void verify() {
        String configured = normalize(properties.botUsername());
        try {
            String actual = client.getMe();
            if (!StringUtils.hasText(actual)) {
                warnIfInvalid(configured);
                return;
            }
            verified = actual;
            if (!actual.equalsIgnoreCase(configured)) {
                log.warn("TELEGRAM_BOT_USERNAME is \"{}\", but the token belongs to @{}. Connect links use @{}; "
                        + "set TELEGRAM_BOT_USERNAME={} to match", properties.botUsername(), actual, actual, actual);
            }
        } catch (TelegramException ex) {
            String hint = ex.getStatusCode() == 401 || ex.getStatusCode() == 404 ? " (check TELEGRAM_BOT_TOKEN)" : "";
            log.warn("Couldn't check the Telegram bot's username: {}{}", ex.getMessage(), hint);
            warnIfInvalid(configured);
        }
    }

    /** The username to link to, without {@code @}. */
    public String username() {
        String name = verified;
        return name != null ? name : normalize(properties.botUsername());
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String name = LINK_PREFIX.matcher(value.trim()).replaceFirst("");
        int end = indexOfAny(name, "?/#");
        if (end >= 0) {
            name = name.substring(0, end);
        }
        return name.startsWith("@") ? name.substring(1).trim() : name.trim();
    }

    private void warnIfInvalid(String name) {
        if (!BOT_USERNAME.matcher(name).matches() || !name.toLowerCase(Locale.ROOT).endsWith("bot")) {
            log.warn("TELEGRAM_BOT_USERNAME \"{}\" isn't a bot username like CourseAuraHubBot (5-32 letters, digits "
                    + "or _, ending in \"bot\"), so connect links won't open the bot", properties.botUsername());
        }
    }

    private static int indexOfAny(String value, String characters) {
        for (int i = 0; i < value.length(); i++) {
            if (characters.indexOf(value.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
