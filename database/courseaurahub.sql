\set ON_ERROR_STOP on

-- Run with: psql -U <user> -f database/courseaurahub.sql
SELECT format('CREATE DATABASE %I', 'courseaurahub')
WHERE NOT EXISTS (
  SELECT 1 FROM pg_database WHERE datname = 'courseaurahub'
)
\gexec

\connect courseaurahub

BEGIN;

-- Authentication & Users
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  first_name VARCHAR(100) NOT NULL,
  last_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password VARCHAR(255),
  role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
  provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
  provider_id VARCHAR(255),
  image_url VARCHAR(500),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

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

CREATE TABLE IF NOT EXISTS course_tags (
  course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  tag_order smallint NOT NULL,
  tag text NOT NULL,
  PRIMARY KEY (course_id, tag_order)
);

CREATE TABLE IF NOT EXISTS course_monthly_metrics (
  course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  month smallint NOT NULL CHECK (month BETWEEN 1 AND 12),
  enrollments integer NOT NULL CHECK (enrollments >= 0),
  revenue numeric(12, 2) NOT NULL CHECK (revenue >= 0),
  PRIMARY KEY (course_id, month)
);

CREATE TABLE IF NOT EXISTS students (
  id integer PRIMARY KEY,
  name text NOT NULL,
  email text NOT NULL UNIQUE,
  avatar text NOT NULL,
  total_spent numeric(10, 2) NOT NULL DEFAULT 0 CHECK (total_spent >= 0),
  joined_at date NOT NULL,
  last_active date NOT NULL,
  status text NOT NULL,
  country text NOT NULL
);

CREATE TABLE IF NOT EXISTS student_courses (
  student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  completed boolean NOT NULL DEFAULT false,
  PRIMARY KEY (student_id, course_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
  id integer PRIMARY KEY,
  student_id integer NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  course_id integer NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  enrolled_at date NOT NULL,
  amount numeric(10, 2) NOT NULL CHECK (amount >= 0)
);

CREATE TABLE IF NOT EXISTS platform_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  platform_name text NOT NULL,
  platform_tagline text NOT NULL,
  currency text NOT NULL,
  default_language text NOT NULL,
  notifications jsonb NOT NULL,
  profile jsonb NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_provider ON users(provider, provider_id);
CREATE INDEX IF NOT EXISTS idx_courses_status ON courses(status);
CREATE INDEX IF NOT EXISTS idx_courses_category ON courses(category);
CREATE INDEX IF NOT EXISTS idx_students_status ON students(status);
CREATE INDEX IF NOT EXISTS idx_enrollments_enrolled_at ON enrollments(enrolled_at DESC);
CREATE INDEX IF NOT EXISTS idx_student_courses_course_id ON student_courses(course_id);

COMMIT;
