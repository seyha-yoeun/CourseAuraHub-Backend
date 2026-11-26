package com.course_aura_hub.chat.model;

/** Who may start a direct conversation with a user. Course staff can always message learners of their course. */
public enum DirectMessagePolicy {
    /** Anyone who shares a course with me (the default). */
    COURSE_MEMBERS,
    /** Only staff of a course we share. */
    STAFF_ONLY,
    /** Nobody, except staff of a course we share. */
    NOBODY
}
