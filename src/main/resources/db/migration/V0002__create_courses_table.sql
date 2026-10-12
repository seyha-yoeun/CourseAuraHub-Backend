-- V0002: Create courses table and indexes
CREATE TABLE IF NOT EXISTS courses (
    id integer PRIMARY KEY,
    slug text NOT NULL UNIQUE,
    title text NOT NULL,
    description text NOT NULL,
    instructor text NOT NULL,
    price numeric(10, 2) NOT NULL CHECK (price >= 0),
    original_price numeric(10, 2) NOT NULL CHECK (original_price >= 0),
    status text NOT NULL,
    category text NOT NULL,
    level text NOT NULL,
    thumbnail text NOT NULL,
    rating numeric(3, 2) NOT NULL CHECK (rating BETWEEN 0 AND 5),
    total_students integer NOT NULL DEFAULT 0 CHECK (total_students >= 0),
    total_lessons integer NOT NULL DEFAULT 0 CHECK (total_lessons >= 0),
    total_hours numeric(8, 2) NOT NULL DEFAULT 0 CHECK (total_hours >= 0),
    revenue numeric(12, 2) NOT NULL DEFAULT 0 CHECK (revenue >= 0),
    completion_rate numeric(5, 2) NOT NULL DEFAULT 0 CHECK (completion_rate BETWEEN 0 AND 100),
    created_at date NOT NULL,
    updated_at date NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_courses_status ON courses(status);
CREATE INDEX IF NOT EXISTS idx_courses_category ON courses(category);
