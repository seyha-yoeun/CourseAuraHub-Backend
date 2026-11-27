package com.course_aura_hub.chat.model;

/** Why a participant is no longer in a group. */
public enum LeftReason {
    LEFT,
    REMOVED,
    /** Lost access to the course (refund, unenrollment, disabled account). Re-enrolling does not restore it. */
    INELIGIBLE
}
