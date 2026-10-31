package com.course_aura_hub.telegram.client;

import com.course_aura_hub.telegram.config.TelegramProperties;
import com.course_aura_hub.telegram.dto.TelegramUpdate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Telegram Bot API. The bot token is part of every URL, and HTTP client errors quote the URL,
 * so failures are rethrown without their cause to keep the token out of logs.
 */
@Component
public class TelegramClient {

    private static final String API = "https://api.telegram.org/bot{token}/{method}";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final TelegramProperties properties;
    private final RestClient restClient;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Sends an HTML message. {@code button} may be null. */
    public void sendMessage(long chatId, String html, Button button) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("link_preview_options", Map.of("is_disabled", true));
        if (button != null) {
            body.put("reply_markup", Map.of("inline_keyboard",
                    List.of(List.of(Map.of("text", button.text(), "url", button.url())))));
        }
        post("sendMessage", body, Void.class);
    }

    public List<TelegramUpdate> getUpdates(long offset) {
        UpdatesResponse response = post("getUpdates", Map.of("offset", offset, "timeout", 0), UpdatesResponse.class);
        return response == null || response.result() == null ? List.of() : response.result();
    }

    public void setWebhook(String url, String secret) {
        post("setWebhook", Map.of("url", url, "secret_token", secret, "allowed_updates", List.of("message")), Void.class);
    }

    public void deleteWebhook() {
        post("deleteWebhook", Map.of(), Void.class);
    }

    private <T> T post(String method, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(API, properties.botToken(), method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException ex) {
            throw failure(method, ex.getStatusCode());
        } catch (RestClientException ex) {
            throw new TelegramException(method + " failed: " + ex.getClass().getSimpleName(), false);
        }
    }

    private TelegramException failure(String method, HttpStatusCode status) {
        return new TelegramException(method + " returned " + status.value(), status.value() == 403);
    }

    public record Button(String text, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UpdatesResponse(List<TelegramUpdate> result) {
    }
}
