-- V0003: Create course_tags table
CREATE TABLE IF NOT EXISTS course_tags (
    course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    tag_order smallint NOT NULL,
    tag text NOT NULL,
    PRIMARY KEY (course_id, tag_order)
);
