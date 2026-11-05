-- V0050: Full-text search for the catalog. Weighted so title matches outrank category, instructor and description.
-- Stemmed with the English configuration; other scripts (such as Khmer) still match through the substring fallback.

ALTER TABLE courses ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A')
        || setweight(to_tsvector('english', coalesce(category, '')), 'B')
        || setweight(to_tsvector('english', coalesce(instructor, '')), 'C')
        || setweight(to_tsvector('english', coalesce(description, '')), 'D')
    ) STORED;

CREATE INDEX IF NOT EXISTS idx_courses_search_vector ON courses USING gin (search_vector);
