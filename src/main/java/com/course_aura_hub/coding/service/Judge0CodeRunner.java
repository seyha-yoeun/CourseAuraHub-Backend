package com.course_aura_hub.coding.service;

import com.course_aura_hub.coding.config.CodingProperties;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs code through a Judge0 CE server with one synchronous submission per test. */
@Slf4j
@Component
public class Judge0CodeRunner implements CodeRunner {

    private final CodingProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Judge0CodeRunner(CodingProperties properties) {
        this.properties = properties;
    }

    @Override
    public RunOutcome run(int languageId, String code, String stdin, BigDecimal timeLimitSeconds, int memoryLimitMb) {
        if (!properties.isConfigured()) {
            throw new ServiceUnavailableException("Running code is not available right now");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language_id", languageId);
        body.put("source_code", encode(code));
        body.put("stdin", encode(stdin == null ? "" : stdin));
        body.put("cpu_time_limit", timeLimitSeconds);
        body.put("wall_time_limit", timeLimitSeconds.multiply(BigDecimal.valueOf(3)));
        body.put("memory_limit", memoryLimitMb * 1024);
        // The sandbox's own network is off by default in Judge0; never ask for it
        body.put("enable_network", false);

        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(
                            properties.judge0Url().replaceAll("/+$", "") + "/submissions?base64_encoded=true&wait=true"))
                    .timeout(properties.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (StringUtils.hasText(properties.authToken())) {
                request.header(properties.authHeader(), properties.authToken());
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("Judge0 answered {}", response.statusCode());
                throw new ServiceUnavailableException("The code runner is unavailable. Please try again.");
            }
            JsonNode json = objectMapper.readTree(response.body());
            return new RunOutcome(status(json.path("status").path("id").asInt()), decode(json, "stdout"),
                    decode(json, "stderr"), decode(json, "compile_output"));
        } catch (IOException ex) {
            log.warn("Judge0 call failed: {}", ex.getMessage());
            throw new ServiceUnavailableException("The code runner is unavailable. Please try again.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("The code run was interrupted", ex);
        }
    }

    /** Judge0 status ids: 3 accepted, 4 wrong answer, 5 time limit, 6 compile error, 7-12 runtime errors. */
    private static String status(int id) {
        return switch (id) {
            case 3, 4 -> "accepted";
            case 5 -> "time_limit";
            case 6 -> "compile_error";
            case 7, 8, 9, 10, 11, 12 -> "runtime_error";
            default -> "error";
        };
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return new String(Base64.getMimeDecoder().decode(value.asText()), StandardCharsets.UTF_8);
    }
}
