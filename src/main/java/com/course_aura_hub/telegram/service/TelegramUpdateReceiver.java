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
import java.util.List;

/**
 * Registers the webhook, or polls when there is no public URL. Telegram allows one receiver per bot, so only the
 * instance holding the polling lease polls; it keeps the lease while it runs, and another instance takes over
 * within {@link #POLL_LEASE} if it stops. Production should still set a webhook URL.
 *
 * <p>Polled updates go to the same {@link TelegramBotService#handle} as webhook calls, and each handled batch is
 * confirmed straight away, so like a webhook delivery an update is handled once even if this instance restarts.
 * Polls don't wait for updates ({@code timeout 0}): they run on the shared scheduler thread.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramUpdateReceiver {

    private final TelegramClient client;
    private final TelegramBotService botService;
    private final TelegramProperties properties;
    private final ClusterJobLock jobLock;
    private final TelegramBotIdentity identity;

    static final Duration POLL_LEASE = Duration.ofSeconds(30);

    private long nextOffset;
    // The failure last logged, so a broken setup is reported once instead of on every poll
    private String failure;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isConfigured()) {
            return;
        }
        // Before anything else: connect links must name the bot the token really belongs to
        identity.verify();
        try {
            if (properties.usesPolling()) {
                client.deleteWebhook();
                log.info("Telegram bot @{} receives messages by polling; set TELEGRAM_WEBHOOK_URL to use a webhook",
                        identity.username());
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
                List<TelegramUpdate> updates = client.getUpdates(nextOffset);
                for (TelegramUpdate update : updates) {
                    nextOffset = update.updateId() + 1;
                    botService.handle(update);
                }
                if (!updates.isEmpty()) {
                    client.confirmUpdates(nextOffset);
                }
                recovered();
            } catch (TelegramException ex) {
                reportFailure(ex);
            }
        });
    }

    private void reportFailure(TelegramException ex) {
        String hint = switch (ex.getStatusCode()) {
            case 401, 404 -> " (check TELEGRAM_BOT_TOKEN)";
            case 409 -> " (another program is polling this bot, or a webhook is set for it; only one can receive updates)";
            default -> "";
        };
        String summary = ex.getMessage() + hint;
        if (summary.equals(failure)) {
            log.debug("Telegram polling still failing: {}", summary);
            return;
        }
        failure = summary;
        log.warn("Telegram polling failed: {}", summary);
    }

    private void recovered() {
        if (failure != null) {
            log.info("Telegram polling works again");
            failure = null;
        }
    }
}
