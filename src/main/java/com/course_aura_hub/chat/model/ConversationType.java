package com.course_aura_hub.chat.model;

/** The four kinds of chat conversation. */
public enum ConversationType {
    /** Two people who share a course. */
    DIRECT,
    /** The official channel of one course: its enrolled learners and staff. */
    COURSE,
    /** A private group inside one course; people join by accepting an invite. */
    GROUP,
    /** One user and the admin team. */
    SUPPORT
}
