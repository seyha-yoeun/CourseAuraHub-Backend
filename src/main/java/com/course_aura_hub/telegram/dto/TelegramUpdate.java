package com.course_aura_hub.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The parts of a Telegram update the bot reads. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        Message message
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String text, Chat chat, From from) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(
            @JsonProperty("first_name") String firstName,
            String username
    ) {
    }
}
