package com.course_aura_hub.telegram.service;

import com.course_aura_hub.telegram.client.TelegramClient;
import com.course_aura_hub.telegram.client.TelegramException;
import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.dto.TelegramUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Registers the webhook, or polls when there is no public URL. Telegram allows one receiver per bot,
 * so polling suits a single instance and production should set a webhook URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateReceiver {

    private final TelegramClient client;
    private final TelegramBotService botService;
    private final TelegramProperties properties;

    private long nextOffset;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isConfigured()) {
            return;
        }
        try {
            if (properties.usesPolling()) {
                client.deleteWebhook();
            } else if (StringUtils.hasText(properties.webhookSecret())) {
                client.setWebhook(properties.webhookUrl(), properties.webhookSecret());
            } else {
                log.error("TELEGRAM_WEBHOOK_SECRET is required with a webhook URL; the bot won't receive messages");
            }
        } catch (TelegramException ex) {
            log.warn("Telegram setup failed: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.telegram.poll-interval:PT3S}")
    public void poll() {
        if (!properties.usesPolling()) {
            return;
        }
        try {
            for (TelegramUpdate update : client.getUpdates(nextOffset)) {
                nextOffset = update.updateId() + 1;
                botService.handle(update);
            }
        } catch (TelegramException ex) {
            log.debug("Telegram poll failed: {}", ex.getMessage());
        }
    }
}
