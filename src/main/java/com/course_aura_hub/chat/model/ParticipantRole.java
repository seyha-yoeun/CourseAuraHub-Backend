package com.course_aura_hub.chat.model;

/** A participant's role. Only groups use OWNER and ADMIN; everyone else is a MEMBER. */
public enum ParticipantRole {
    OWNER,
    ADMIN,
    MEMBER
}
