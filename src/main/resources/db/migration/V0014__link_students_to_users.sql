-- V0014: Link learner records (students) to auth identities (users).

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL;

UPDATE students s
SET user_id = u.id
FROM users u
WHERE s.user_id IS NULL
  AND LOWER(s.email) = LOWER(u.email);
