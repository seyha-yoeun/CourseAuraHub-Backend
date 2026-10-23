-- V0012: Indexes used by the domain query layer.

CREATE INDEX IF NOT EXISTS idx_courses_created_at ON courses (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_courses_title_lower ON courses (LOWER(title));
CREATE INDEX IF NOT EXISTS idx_students_last_active ON students (last_active DESC);
CREATE INDEX IF NOT EXISTS idx_enrollments_student_id ON enrollments (student_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_course_id ON enrollments (course_id);
CREATE INDEX IF NOT EXISTS idx_course_monthly_metrics_month ON course_monthly_metrics (month);
