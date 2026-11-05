-- V0051: Pacing. A sequential course unlocks each lesson once the ones before it are complete;
-- a lesson with release_after_days opens that many days after the learner enrolled.

ALTER TABLE courses ADD COLUMN IF NOT EXISTS sequential_lessons boolean NOT NULL DEFAULT false;

ALTER TABLE lessons ADD COLUMN IF NOT EXISTS release_after_days integer
    CHECK (release_after_days BETWEEN 1 AND 3650);
