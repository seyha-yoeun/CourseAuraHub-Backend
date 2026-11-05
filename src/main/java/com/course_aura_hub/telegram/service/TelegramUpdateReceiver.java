package com.course_aura_hub.telegram.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
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

import java.time.Duration;

/**
 * Registers the webhook, or polls when there is no public URL. Telegram allows one receiver per bot, so only the
 * instance holding the polling lease polls; it keeps the lease while it runs, and another instance takes over
 * within {@link #POLL_LEASE} if it stops. Production should still set a webhook URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateReceiver {

    private final TelegramClient client;
    private final TelegramBotService botService;
    private final TelegramProperties properties;
    private final ClusterJobLock jobLock;

    static final Duration POLL_LEASE = Duration.ofSeconds(30);

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
        jobLock.runExclusively("telegram-polling", Duration.ofMinutes(1), POLL_LEASE, () -> {
            try {
                for (TelegramUpdate update : client.getUpdates(nextOffset)) {
                    nextOffset = update.updateId() + 1;
                    botService.handle(update);
                }
            } catch (TelegramException ex) {
                log.debug("Telegram poll failed: {}", ex.getMessage());
            }
        });
    }
}
