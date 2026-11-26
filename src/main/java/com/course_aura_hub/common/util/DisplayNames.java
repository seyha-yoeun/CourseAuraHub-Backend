package com.course_aura_hub.common.util;

import org.springframework.util.StringUtils;

/** How people are named to other learners: a first name and an initial, never a full name or an email. */
public final class DisplayNames {

    private DisplayNames() {
    }

    /** "Jane Learner" becomes "Jane L."; a blank name becomes "Learner". */
    public static String shortName(String name) {
        if (!StringUtils.hasText(name)) {
            return "Learner";
        }
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    /** First and last name joined, or "Learner" when both are blank. */
    public static String fullName(String firstName, String lastName) {
        String name = ((firstName == null ? "" : firstName.trim()) + " " + (lastName == null ? "" : lastName.trim())).trim();
        return name.isEmpty() ? "Learner" : name;
    }
}
