-- V0032: A certificate records what was true when it was issued, so renaming a learner, course or
-- instructor later must not rewrite it. Existing rows take today's values, the best record available.

ALTER TABLE course_certificates ADD COLUMN IF NOT EXISTS learner_name text;
ALTER TABLE course_certificates ADD COLUMN IF NOT EXISTS course_title text;
ALTER TABLE course_certificates ADD COLUMN IF NOT EXISTS instructor text;

UPDATE course_certificates certificate
SET learner_name = student.name,
    course_title = course.title,
    instructor = course.instructor
FROM students student, courses course
WHERE student.id = certificate.student_id
  AND course.id = certificate.course_id
  AND certificate.learner_name IS NULL;

ALTER TABLE course_certificates ALTER COLUMN learner_name SET NOT NULL;
ALTER TABLE course_certificates ALTER COLUMN course_title SET NOT NULL;
ALTER TABLE course_certificates ALTER COLUMN instructor SET NOT NULL;
