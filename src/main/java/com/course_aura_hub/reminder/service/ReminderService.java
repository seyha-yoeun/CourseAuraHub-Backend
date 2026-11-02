package com.course_aura_hub.reminder.service;

public interface ReminderService {

    /** @return how many learners were nudged */
    int sendDue();

    boolean isEnabled(String email);

    boolean setEnabled(String email, boolean enabled);
}
