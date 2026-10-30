-- V0015: Learner wishlist (courses saved for later).

CREATE TABLE IF NOT EXISTS wishlist_items (
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, course_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_items_course_id ON wishlist_items(course_id);
