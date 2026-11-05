-- V0063: Courses a learner should finish first. A hard prerequisite blocks enrolling and checkout until the
-- required course is complete; a soft one is only a recommendation.

CREATE TABLE IF NOT EXISTS course_prerequisites (
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    required_course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    hard boolean NOT NULL DEFAULT true,
    position integer NOT NULL DEFAULT 0,
    PRIMARY KEY (course_id, required_course_id),
    CHECK (course_id <> required_course_id)
);

CREATE INDEX IF NOT EXISTS idx_course_prerequisites_required ON course_prerequisites(required_course_id);
