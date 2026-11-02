-- V0045: When each learner was last nudged about a course, and whether they want nudges at all.

ALTER TABLE students ADD COLUMN IF NOT EXISTS reminders_enabled boolean NOT NULL DEFAULT true;

CREATE TABLE IF NOT EXISTS learner_reminders (
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    sent_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, course_id)
);
