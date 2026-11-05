package com.course_aura_hub.export.service;

import com.course_aura_hub.common.security.Actor;

import java.io.Writer;
import java.time.LocalDate;

/** Writes CSV exports. Admin exports include contact details; instructor exports never do. */
public interface ExportService {

    /** Checks the filters before any bytes are written, so a mistake is a normal error response. */
    void validateOrderStatus(String status);

    /** Checks access before any bytes are written. */
    void requireCourseManager(Actor actor, String slug);

    void orders(Writer out, LocalDate from, LocalDate to, String status);

    void enrollments(Writer out, LocalDate from, LocalDate to);

    void learners(Writer out);

    void memberCardLedger(Writer out, LocalDate from, LocalDate to);

    void auditLog(Writer out, LocalDate from, LocalDate to);

    /** A course's learners and their progress, for its managers: names only. */
    void courseLearners(Writer out, Actor actor, String slug);

    /** A course's assignment submissions and grades, for its managers: names only. */
    void courseGrades(Writer out, Actor actor, String slug);
}
