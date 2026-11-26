package com.course_aura_hub.chat.model;

/** Lifecycle of a conversation: groups are ACTIVE or ARCHIVED, support threads OPEN or CLOSED; DIRECT and COURSE stay ACTIVE. */
public enum ConversationStatus {
    ACTIVE,
    ARCHIVED,
    OPEN,
    CLOSED
}
