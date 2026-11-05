-- V0054: Quiz rules (attempt limit, retry cooldown, time limit, question pool, shuffling),
-- server-side quiz sessions for timed or randomised quizzes, and extra attempts granted by staff.

ALTER TABLE lessons ADD COLUMN IF NOT EXISTS quiz_max_attempts integer
    CHECK (quiz_max_attempts BETWEEN 1 AND 100);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS quiz_retry_cooldown_minutes integer
    CHECK (quiz_retry_cooldown_minutes BETWEEN 1 AND 10080);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS quiz_time_limit_minutes integer
    CHECK (quiz_time_limit_minutes BETWEEN 1 AND 600);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS quiz_questions_per_attempt integer
    CHECK (quiz_questions_per_attempt BETWEEN 1 AND 200);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS quiz_shuffle boolean NOT NULL DEFAULT false;

ALTER TABLE quiz_attempts ADD COLUMN IF NOT EXISTS started_at timestamptz;

-- The questions one attempt drew, in the order shown, and when it must be submitted
CREATE TABLE IF NOT EXISTS quiz_sessions (
    id bigserial PRIMARY KEY,
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    lesson_id integer NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    question_ids text NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz,
    submitted_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_sessions_open ON quiz_sessions(student_id, lesson_id) WHERE submitted_at IS NULL;

CREATE TABLE IF NOT EXISTS quiz_attempt_grants (
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    lesson_id integer NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    extra_attempts integer NOT NULL CHECK (extra_attempts BETWEEN 1 AND 1000),
    granted_by uuid REFERENCES users(id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, lesson_id)
);
