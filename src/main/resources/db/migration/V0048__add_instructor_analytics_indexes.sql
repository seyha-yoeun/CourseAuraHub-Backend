-- V0048: Instructor analytics read quiz attempts per lesson; the existing index leads with the student.

CREATE INDEX IF NOT EXISTS idx_quiz_attempts_lesson ON quiz_attempts(lesson_id);
