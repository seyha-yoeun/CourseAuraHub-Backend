-- V0029: Where a learner stopped in a video. Separate from lesson_progress, whose rows mean "completed".

CREATE TABLE IF NOT EXISTS lesson_watch_positions (
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    lesson_id integer NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    position_seconds integer NOT NULL CHECK (position_seconds >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, lesson_id)
);

CREATE INDEX IF NOT EXISTS idx_lesson_watch_positions_lesson_id ON lesson_watch_positions(lesson_id);
