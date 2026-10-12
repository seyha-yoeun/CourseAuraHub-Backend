-- V0006: Create student_courses table and index
CREATE TABLE IF NOT EXISTS student_courses (
    student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    completed boolean NOT NULL DEFAULT false,
    PRIMARY KEY (student_id, course_id)
);

CREATE INDEX IF NOT EXISTS idx_student_courses_course_id ON student_courses(course_id);
