-- V0017: Seed a free starter course and sample curricula so learner flows have data.
-- total_lessons / total_hours are recalculated for every course that receives a curriculum here.

INSERT INTO courses (
    slug, title, description, instructor, price, original_price,
    status, category, level, thumbnail, rating,
    total_students, total_lessons, total_hours, revenue, completion_rate,
    created_at, updated_at
) VALUES (
    'git-github-fundamentals',
    'Git & GitHub Fundamentals',
    'A free, hands-on introduction to version control: commits, branches, pull requests, and collaborative workflows on GitHub.',
    'Alex Chen',
    0.00,
    0.00,
    'PUBLISHED',
    'Web Development',
    'BEGINNER',
    'https://images.unsplash.com/photo-1556075798-4825dfaaf498?w=800&h=450&fit=crop',
    0.00,
    0,
    0,
    0.00,
    0.00,
    0.00,
    '2026-10-01',
    '2026-10-01'
)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO course_monthly_metrics (course_id, month, enrollments, revenue)
SELECT c.id, m.month, 0, 0.00
FROM courses c
CROSS JOIN generate_series(1, 12) AS m(month)
WHERE c.slug = 'git-github-fundamentals'
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    v_course_id integer;
    v_section_id integer;
BEGIN
    -- Git & GitHub Fundamentals (free)
    SELECT id INTO v_course_id FROM courses WHERE slug = 'git-github-fundamentals';
    IF v_course_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_sections WHERE course_id = v_course_id) THEN
        INSERT INTO course_sections (course_id, title, position) VALUES (v_course_id, 'Getting Started', 0)
        RETURNING id INTO v_section_id;
        INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position) VALUES
            (v_section_id, 'What is version control?', 'VIDEO', 8, 'https://cdn.courseaurahub.io/git/intro.mp4', true, 0),
            (v_section_id, 'Installing and configuring Git', 'ARTICLE', 10, 'https://cdn.courseaurahub.io/git/install', false, 1);

        INSERT INTO course_sections (course_id, title, position) VALUES (v_course_id, 'Everyday Git', 1)
        RETURNING id INTO v_section_id;
        INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position) VALUES
            (v_section_id, 'Commits and history', 'VIDEO', 14, 'https://cdn.courseaurahub.io/git/commits.mp4', false, 0),
            (v_section_id, 'Branching and merging', 'VIDEO', 18, 'https://cdn.courseaurahub.io/git/branches.mp4', false, 1),
            (v_section_id, 'Pull requests on GitHub', 'VIDEO', 15, 'https://cdn.courseaurahub.io/git/pull-requests.mp4', false, 2),
            (v_section_id, 'Check your knowledge', 'QUIZ', 5, 'https://cdn.courseaurahub.io/git/quiz', false, 3);
    END IF;

    -- Nuxt 3 & Vue 3 Enterprise Architecture
    SELECT id INTO v_course_id FROM courses WHERE slug = 'nuxt-3-mastery';
    IF v_course_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_sections WHERE course_id = v_course_id) THEN
        INSERT INTO course_sections (course_id, title, position) VALUES (v_course_id, 'Foundations', 0)
        RETURNING id INTO v_section_id;
        INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position) VALUES
            (v_section_id, 'Course overview', 'VIDEO', 6, 'https://cdn.courseaurahub.io/nuxt/overview.mp4', true, 0),
            (v_section_id, 'Project setup with Nuxt 3', 'VIDEO', 22, 'https://cdn.courseaurahub.io/nuxt/setup.mp4', false, 1),
            (v_section_id, 'Rendering modes explained', 'ARTICLE', 15, 'https://cdn.courseaurahub.io/nuxt/rendering', false, 2);

        INSERT INTO course_sections (course_id, title, position) VALUES (v_course_id, 'State & Data', 1)
        RETURNING id INTO v_section_id;
        INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position) VALUES
            (v_section_id, 'Pinia store architecture', 'VIDEO', 28, 'https://cdn.courseaurahub.io/nuxt/pinia.mp4', false, 0),
            (v_section_id, 'Data fetching with useFetch', 'VIDEO', 24, 'https://cdn.courseaurahub.io/nuxt/fetch.mp4', false, 1),
            (v_section_id, 'Section quiz', 'QUIZ', 10, 'https://cdn.courseaurahub.io/nuxt/quiz-1', false, 2);
    END IF;

    -- Spring Boot 3 & Distributed Microservices Masterclass
    SELECT id INTO v_course_id FROM courses WHERE slug = 'spring-boot-microservices';
    IF v_course_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_sections WHERE course_id = v_course_id) THEN
        INSERT INTO course_sections (course_id, title, position) VALUES (v_course_id, 'Spring Boot Essentials', 0)
        RETURNING id INTO v_section_id;
        INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position) VALUES
            (v_section_id, 'Welcome and roadmap', 'VIDEO', 7, 'https://cdn.courseaurahub.io/spring/welcome.mp4', true, 0),
            (v_section_id, 'Building your first REST API', 'VIDEO', 32, 'https://cdn.courseaurahub.io/spring/rest.mp4', false, 1);

        INSERT INTO course_sections (course_id, title, position) VALUES (v_course_id, 'Going Distributed', 1)
        RETURNING id INTO v_section_id;
        INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position) VALUES
            (v_section_id, 'Service discovery and gateways', 'VIDEO', 35, 'https://cdn.courseaurahub.io/spring/gateway.mp4', false, 0),
            (v_section_id, 'Event streaming with Kafka', 'VIDEO', 40, 'https://cdn.courseaurahub.io/spring/kafka.mp4', false, 1),
            (v_section_id, 'Resilience patterns', 'ARTICLE', 20, 'https://cdn.courseaurahub.io/spring/resilience', false, 2);
    END IF;
END $$;

UPDATE courses c
SET total_lessons = stats.lesson_count,
    total_hours = ROUND(stats.minutes / 60.0, 2)
FROM (
    SELECT s.course_id, COUNT(l.id) AS lesson_count, COALESCE(SUM(l.duration_minutes), 0) AS minutes
    FROM course_sections s
    JOIN lessons l ON l.section_id = s.id
    GROUP BY s.course_id
) stats
WHERE c.id = stats.course_id;
