package com.course_aura_hub.settings.dto;

public record NotificationPreferences(
        Boolean newEnrollment,
        Boolean courseCompletion,
        Boolean weeklyReport,
        Boolean marketingEmails
) {
    public boolean newEnrollmentEnabled() {
        return Boolean.TRUE.equals(newEnrollment);
    }

    public boolean courseCompletionEnabled() {
        return Boolean.TRUE.equals(courseCompletion);
    }

    public boolean weeklyReportEnabled() {
        return Boolean.TRUE.equals(weeklyReport);
    }

    public boolean marketingEmailsEnabled() {
        return Boolean.TRUE.equals(marketingEmails);
    }
}
