package com.course_aura_hub.telegram.controller;

import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.dto.TelegramUpdate;
import com.course_aura_hub.telegram.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotService botService;
    private final TelegramProperties properties;

    @PostMapping("/api/v1/telegram/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody TelegramUpdate update
    ) {
        if (!properties.isConfigured() || !matchesSecret(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        botService.handle(update);
        return ResponseEntity.ok().build();
    }

    private boolean matchesSecret(String secret) {
        return StringUtils.hasText(properties.webhookSecret())
                && secret != null
                && MessageDigest.isEqual(
                        secret.getBytes(StandardCharsets.UTF_8),
                        properties.webhookSecret().getBytes(StandardCharsets.UTF_8));
    }
}
