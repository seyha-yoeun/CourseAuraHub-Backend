-- V0046: Indexes for course recommendations (tag overlap and co-enrollment lookups).

CREATE INDEX IF NOT EXISTS idx_course_tags_tag_lower ON course_tags (lower(tag));
CREATE INDEX IF NOT EXISTS idx_wishlist_items_student_id ON wishlist_items (student_id);
