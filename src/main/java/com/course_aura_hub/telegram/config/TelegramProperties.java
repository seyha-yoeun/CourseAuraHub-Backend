package com.course_aura_hub.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * @param botToken      token from BotFather; blank turns Telegram off
 * @param botUsername   the bot's username without {@code @}, used in connect links
 * @param webhookUrl    public URL of the webhook; blank polls Telegram instead, which suits local development
 * @param webhookSecret value Telegram must send back on every webhook call
 * @param appUrl        frontend origin that notification links open in
 */
@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        String botToken,
        String botUsername,
        String webhookUrl,
        String webhookSecret,
        @DefaultValue("http://localhost:3000") String appUrl
) {

    public boolean isConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(botUsername);
    }

    public boolean usesPolling() {
        return isConfigured() && !StringUtils.hasText(webhookUrl);
    }
}
