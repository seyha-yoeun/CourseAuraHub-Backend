-- V0070: A learner's weekly lesson goal and whether they appear on leaderboards. Leaderboard XP itself is derived
-- from lesson progress, quiz attempts and certificates, never stored.

CREATE TABLE IF NOT EXISTS learner_goals (
    student_id integer PRIMARY KEY REFERENCES students(id) ON DELETE CASCADE,
    weekly_lessons_target integer CHECK (weekly_lessons_target BETWEEN 1 AND 100),
    leaderboard_visible boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Leaderboards read recent activity by time
CREATE INDEX IF NOT EXISTS idx_lesson_progress_completed_at ON lesson_progress(completed_at);
CREATE INDEX IF NOT EXISTS idx_course_certificates_issued_at ON course_certificates(issued_at);
