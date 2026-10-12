-- V0004: Create course_monthly_metrics table
CREATE TABLE IF NOT EXISTS course_monthly_metrics (
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    month smallint NOT NULL CHECK (month BETWEEN 1 AND 12),
    enrollments integer NOT NULL CHECK (enrollments >= 0),
    revenue numeric(12, 2) NOT NULL CHECK (revenue >= 0),
    PRIMARY KEY (course_id, month)
);
