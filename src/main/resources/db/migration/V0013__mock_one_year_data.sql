-- V0013: Mock one full year of realistic production-grade data across all tables
-- Covers courses, tags, monthly metrics (months 1-12), students, course enrollments,
-- student ledger transactions, platform settings, and user profiles.

-- 1. Platform Settings
INSERT INTO platform_settings (id, platform_name, platform_tagline, currency, default_language, notifications, profile)
VALUES (
    1,
    'CourseAuraHub Academy',
    'Master Modern Tech, Engineering & Design',
    'USD',
    'en',
    '{"newEnrollment": true, "courseCompletion": true, "weeklyReport": true, "marketingEmails": false}'::jsonb,
    '{"name": "Alex Chen", "role": "Platform Director", "email": "admin@courseaurahub.io", "avatar": "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&fit=crop&q=80", "bio": "Lead Architect & Educator with 10+ years building scalable distributed platforms and web systems.", "website": "https://courseaurahub.io", "twitter": "@alexchen_dev"}'::jsonb
)
ON CONFLICT (id) DO UPDATE SET
    platform_name = EXCLUDED.platform_name,
    platform_tagline = EXCLUDED.platform_tagline,
    currency = EXCLUDED.currency,
    default_language = EXCLUDED.default_language,
    notifications = EXCLUDED.notifications,
    profile = EXCLUDED.profile;

-- 2. Courses (8 comprehensive courses covering Web, Backend, DevOps, AI, Design, Mobile, Security)
INSERT INTO courses (
    id, slug, title, description, instructor, price, original_price,
    status, category, level, thumbnail, rating,
    total_students, total_lessons, total_hours, revenue, completion_rate,
    created_at, updated_at
) VALUES
(
    1,
    'nuxt-3-mastery',
    'Nuxt 3 & Vue 3 Enterprise Architecture',
    'The complete guide to production Nuxt 3 covering SSR, SSG, hybrid rendering, Nitro server engine, Pinia state architecture, and enterprise deployment.',
    'Alex Chen',
    129.99,
    199.99,
    'PUBLISHED',
    'Web Development',
    'INTERMEDIATE',
    'https://images.unsplash.com/photo-1618477388954-7852f32655ec?w=800&h=450&fit=crop',
    4.90,
    372,
    96,
    24.50,
    48356.28,
    78.50,
    '2025-10-15',
    '2026-10-20'
),
(
    2,
    'spring-boot-microservices',
    'Spring Boot 3 & Distributed Microservices Masterclass',
    'Architect resilient, production-grade Spring Boot microservices with Spring Cloud, PostgreSQL, Docker, Kafka event streams, and Kubernetes orchestration.',
    'Elena Rostova',
    149.99,
    229.99,
    'PUBLISHED',
    'Backend Systems',
    'ADVANCED',
    'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=800&h=450&fit=crop',
    4.95,
    305,
    112,
    32.00,
    47246.85,
    82.00,
    '2025-11-01',
    '2026-10-18'
),
(
    3,
    'typescript-for-vue-devs',
    'TypeScript Production Patterns & Generics',
    'Supercharge modern applications with strict TypeScript. Type gymnastics, generics, branded types, and building robust type-safe web systems.',
    'James Wright',
    79.99,
    129.99,
    'PUBLISHED',
    'Programming',
    'BEGINNER',
    'https://images.unsplash.com/photo-1516116216624-53e697fedbea?w=800&h=450&fit=crop',
    4.80,
    266,
    64,
    15.00,
    22077.24,
    74.00,
    '2025-11-15',
    '2026-10-14'
),
(
    4,
    'tailwind-ui-design-system',
    'Figma to Tailwind CSS Design Systems',
    'Build scalable, token-driven design systems with Tailwind CSS. Master dark mode, custom plugins, fluid typography, and enterprise-grade component libraries.',
    'Sofia Park',
    99.99,
    159.99,
    'PUBLISHED',
    'UI/UX Design',
    'INTERMEDIATE',
    'https://images.unsplash.com/photo-1561736778-92e52a7769ef?w=800&h=450&fit=crop',
    4.88,
    297,
    72,
    18.50,
    29696.93,
    85.00,
    '2025-12-01',
    '2026-10-22'
),
(
    5,
    'ai-agents-llm-engineering',
    'Autonomous AI Agents & LLM Application Engineering',
    'Build autonomous agentic workflows, multi-agent systems, function-calling pipelines, RAG with vector databases, and evaluation frameworks using Python and LangGraph.',
    'Marcus Thorne',
    169.99,
    249.99,
    'PUBLISHED',
    'AI & Machine Learning',
    'ADVANCED',
    'https://images.unsplash.com/photo-1677442136019-21780ecad995?w=800&h=450&fit=crop',
    4.98,
    414,
    88,
    28.00,
    72035.76,
    81.50,
    '2025-12-15',
    '2026-10-23'
),
(
    6,
    'docker-kubernetes-devops',
    'Docker, Kubernetes & Cloud Native CI/CD',
    'Containerize full-stack apps and deploy to Kubernetes clusters. Master Helm, ingress controllers, Prometheus monitoring, and automated GitOps with GitHub Actions.',
    'Alex Chen',
    119.99,
    189.99,
    'PUBLISHED',
    'DevOps',
    'ADVANCED',
    'https://images.unsplash.com/photo-1605745341112-85968b19335b?w=800&h=450&fit=crop',
    4.75,
    246,
    80,
    22.00,
    29517.54,
    68.00,
    '2026-01-10',
    '2026-10-19'
),
(
    7,
    'flutter-crossplatform-mobile',
    'Flutter 3 & Dart: Multiplatform App Development',
    'Develop fluid, high-performance mobile applications for iOS and Android using Flutter 3, Riverpod architecture, offline caching, and native platform channels.',
    'Sarah Jenkins',
    89.99,
    139.99,
    'DRAFT',
    'Mobile Development',
    'BEGINNER',
    'https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?w=800&h=450&fit=crop',
    0.00,
    0,
    58,
    16.00,
    0.00,
    0.00,
    '2026-08-20',
    '2026-10-15'
),
(
    8,
    'cloud-security-and-devsecops',
    'Zero-Trust Cloud Security & DevSecOps',
    'Implement automated security scanning, IAM least-privilege policies, HashiCorp Vault secrets management, SAST/DAST pipelines, and SOC2 compliance monitoring.',
    'David Kim',
    139.99,
    209.99,
    'ARCHIVED',
    'Cybersecurity',
    'ADVANCED',
    'https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800&h=450&fit=crop',
    4.70,
    73,
    48,
    14.50,
    10219.27,
    85.00,
    '2025-09-01',
    '2026-07-15'
)
ON CONFLICT (id) DO UPDATE SET
    slug = EXCLUDED.slug,
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    instructor = EXCLUDED.instructor,
    price = EXCLUDED.price,
    original_price = EXCLUDED.original_price,
    status = EXCLUDED.status,
    category = EXCLUDED.category,
    level = EXCLUDED.level,
    thumbnail = EXCLUDED.thumbnail,
    rating = EXCLUDED.rating,
    total_students = EXCLUDED.total_students,
    total_lessons = EXCLUDED.total_lessons,
    total_hours = EXCLUDED.total_hours,
    revenue = EXCLUDED.revenue,
    completion_rate = EXCLUDED.completion_rate,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

-- 3. Course Tags
INSERT INTO course_tags (course_id, tag_order, tag) VALUES
(1, 0, 'Nuxt 3'), (1, 1, 'Vue 3'), (1, 2, 'TypeScript'), (1, 3, 'SSR'), (1, 4, 'Nitro'),
(2, 0, 'Spring Boot'), (2, 1, 'Java 21'), (2, 2, 'Microservices'), (2, 3, 'Kafka'), (2, 4, 'PostgreSQL'),
(3, 0, 'TypeScript'), (3, 1, 'JavaScript'), (3, 2, 'Design Patterns'), (3, 3, 'Generics'),
(4, 0, 'Tailwind CSS'), (4, 1, 'Figma'), (4, 2, 'UI/UX'), (4, 3, 'Design System'),
(5, 0, 'AI Agents'), (5, 1, 'LLMs'), (5, 2, 'Python'), (5, 3, 'LangGraph'), (5, 4, 'Vector DB'),
(6, 0, 'Docker'), (6, 1, 'Kubernetes'), (6, 2, 'DevOps'), (6, 3, 'CI/CD'), (6, 4, 'Helm'),
(7, 0, 'Flutter'), (7, 1, 'Dart'), (7, 2, 'Mobile'), (7, 3, 'iOS'), (7, 4, 'Android'),
(8, 0, 'Cybersecurity'), (8, 1, 'DevSecOps'), (8, 2, 'Cloud Security'), (8, 3, 'Vault')
ON CONFLICT (course_id, tag_order) DO UPDATE SET tag = EXCLUDED.tag;

-- 4. Course Monthly Metrics (Months 1 through 12, covering the complete 1-year performance cycle)
INSERT INTO course_monthly_metrics (course_id, month, enrollments, revenue) VALUES
-- Course 1: Nuxt 3 Mastery (sum = 372 enrollments, $48,356.28 revenue)
(1, 1, 22, 2859.78),
(1, 2, 25, 3249.75),
(1, 3, 28, 3639.72),
(1, 4, 30, 3899.70),
(1, 5, 32, 4159.68),
(1, 6, 35, 4549.65),
(1, 7, 34, 4419.66),
(1, 8, 38, 4939.62),
(1, 9, 36, 4679.64),
(1, 10, 40, 5199.60),
(1, 11, 26, 3379.74),
(1, 12, 26, 3379.74),

-- Course 2: Spring Boot 3 Microservices (sum = 305 enrollments, $47,246.85 revenue)
(2, 1, 18, 2699.82),
(2, 2, 20, 2999.80),
(2, 3, 22, 3299.78),
(2, 4, 25, 3749.75),
(2, 5, 24, 3599.76),
(2, 6, 28, 4199.72),
(2, 7, 30, 4499.70),
(2, 8, 32, 4799.68),
(2, 9, 34, 5099.66),
(2, 10, 38, 5699.62),
(2, 11, 22, 3299.78),
(2, 12, 22, 3299.78),

-- Course 3: TypeScript Production Patterns (sum = 266 enrollments, $22,077.24 revenue)
(3, 1, 15, 1199.85),
(3, 2, 18, 1439.82),
(3, 3, 20, 1599.80),
(3, 4, 22, 1759.78),
(3, 5, 25, 1999.75),
(3, 6, 24, 1919.76),
(3, 7, 26, 2079.74),
(3, 8, 28, 2239.72),
(3, 9, 30, 2399.70),
(3, 10, 32, 2559.68),
(3, 11, 18, 1439.82),
(3, 12, 18, 1439.82),

-- Course 4: Figma to Tailwind CSS (sum = 297 enrollments, $29,696.93 revenue)
(4, 1, 16, 1599.84),
(4, 2, 18, 1799.82),
(4, 3, 22, 2199.78),
(4, 4, 24, 2399.76),
(4, 5, 26, 2599.74),
(4, 6, 28, 2799.72),
(4, 7, 30, 2999.70),
(4, 8, 32, 3199.68),
(4, 9, 35, 3499.65),
(4, 10, 36, 3599.64),
(4, 11, 20, 1999.80),
(4, 12, 20, 1999.80),

-- Course 5: Autonomous AI Agents & LLMs (sum = 414 enrollments, $72,035.76 revenue)
(5, 1, 20, 3399.80),
(5, 2, 24, 4079.76),
(5, 3, 28, 4759.72),
(5, 4, 32, 5439.68),
(5, 5, 35, 5949.65),
(5, 6, 38, 6459.62),
(5, 7, 42, 7139.58),
(5, 8, 45, 7649.55),
(5, 9, 48, 8159.52),
(5, 10, 52, 8839.48),
(5, 11, 30, 5099.70),
(5, 12, 30, 5099.70),

-- Course 6: Docker & Kubernetes DevOps (sum = 246 enrollments, $29,517.54 revenue)
(6, 1, 14, 1679.86),
(6, 2, 16, 1919.84),
(6, 3, 18, 2159.82),
(6, 4, 20, 2399.80),
(6, 5, 22, 2639.78),
(6, 6, 21, 2519.79),
(6, 7, 24, 2879.76),
(6, 8, 25, 2999.75),
(6, 9, 26, 3119.74),
(6, 10, 28, 3359.72),
(6, 11, 16, 1919.84),
(6, 12, 16, 1919.84),

-- Course 7: Flutter 3 Mobile (Draft, 0 metrics)
(7, 1, 0, 0.00), (7, 2, 0, 0.00), (7, 3, 0, 0.00), (7, 4, 0, 0.00),
(7, 5, 0, 0.00), (7, 6, 0, 0.00), (7, 7, 0, 0.00), (7, 8, 0, 0.00),
(7, 9, 0, 0.00), (7, 10, 0, 0.00), (7, 11, 0, 0.00), (7, 12, 0, 0.00),

-- Course 8: Zero-Trust Cloud Security (sum = 73 enrollments, $10,219.27 revenue)
(8, 1, 10, 1399.90),
(8, 2, 12, 1679.88),
(8, 3, 11, 1539.89),
(8, 4, 9, 1259.91),
(8, 5, 8, 1119.92),
(8, 6, 7, 979.93),
(8, 7, 5, 699.95),
(8, 8, 4, 559.96),
(8, 9, 3, 419.97),
(8, 10, 2, 279.98),
(8, 11, 1, 139.99),
(8, 12, 1, 139.99)
ON CONFLICT (course_id, month) DO UPDATE SET
    enrollments = EXCLUDED.enrollments,
    revenue = EXCLUDED.revenue;

-- 5. Students (30 diverse learners across multiple countries and joined dates)
INSERT INTO students (id, name, email, avatar, total_spent, joined_at, last_active, status, country) VALUES
(1, 'Emma Thompson', 'emma.thompson@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Emma&backgroundColor=b6e3f4', 379.97, '2025-10-18', '2026-10-23', 'ACTIVE', 'United States'),
(2, 'Lucas Oliveira', 'lucas.oliveira@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Lucas&backgroundColor=c0aede', 279.98, '2025-11-02', '2026-10-22', 'ACTIVE', 'Brazil'),
(3, 'Yuki Tanaka', 'yuki.tanaka@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Yuki&backgroundColor=ffd5dc', 549.96, '2025-11-20', '2026-10-24', 'ACTIVE', 'Japan'),
(4, 'Carlos Mendez', 'carlos.mendez@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Carlos&backgroundColor=ffdfbf', 149.99, '2025-12-05', '2026-09-18', 'INACTIVE', 'Mexico'),
(5, 'Aisha Patel', 'aisha.patel@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Aisha&backgroundColor=d1f4cc', 399.97, '2025-12-18', '2026-10-21', 'ACTIVE', 'India'),
(6, 'Oliver Schmidt', 'oliver.schmidt@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Oliver&backgroundColor=b6e3f4', 569.96, '2026-01-08', '2026-10-20', 'ACTIVE', 'Germany'),
(7, 'Priya Sharma', 'priya.sharma@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Priya&backgroundColor=ffd5dc', 179.98, '2026-01-22', '2026-10-23', 'ACTIVE', 'India'),
(8, 'Ethan Williams', 'ethan.williams@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Ethan&backgroundColor=c0aede', 129.99, '2026-02-05', '2026-09-12', 'INACTIVE', 'United Kingdom'),
(9, 'Sophie Dubois', 'sophie.dubois@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Sophie&backgroundColor=d1f4cc', 419.97, '2026-02-18', '2026-10-24', 'ACTIVE', 'France'),
(10, 'Min-jun Lee', 'minjun.lee@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=MinJun&backgroundColor=ffdfbf', 329.97, '2026-03-01', '2026-10-22', 'ACTIVE', 'South Korea'),
(11, 'Liam O''Connor', 'liam.oconnor@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Liam&backgroundColor=b6e3f4', 269.98, '2026-03-14', '2026-10-19', 'ACTIVE', 'Ireland'),
(12, 'Ananya Rao', 'ananya.rao@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Ananya&backgroundColor=ffd5dc', 479.96, '2026-03-28', '2026-10-21', 'ACTIVE', 'Singapore'),
(13, 'Gabriel Costa', 'gabriel.costa@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Gabriel&backgroundColor=c0aede', 229.98, '2026-04-09', '2026-10-18', 'ACTIVE', 'Portugal'),
(14, 'Chloe Martin', 'chloe.martin@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Chloe&backgroundColor=d1f4cc', 379.97, '2026-04-20', '2026-10-23', 'ACTIVE', 'Canada'),
(15, 'Chen Wei', 'chen.wei@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=ChenWei&backgroundColor=ffdfbf', 549.96, '2026-05-02', '2026-10-22', 'ACTIVE', 'Taiwan'),
(16, 'Fatima Al-Zahra', 'fatima.alzahra@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Fatima&backgroundColor=ffd5dc', 419.97, '2026-05-15', '2026-10-24', 'ACTIVE', 'United Arab Emirates'),
(17, 'Mateo Hernandez', 'mateo.hernandez@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Mateo&backgroundColor=c0aede', 119.99, '2026-05-29', '2026-09-25', 'INACTIVE', 'Spain'),
(18, 'Hannah Mueller', 'hannah.mueller@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Hannah&backgroundColor=b6e3f4', 399.97, '2026-06-11', '2026-10-20', 'ACTIVE', 'Switzerland'),
(19, 'Dimitri Ivanov', 'dimitri.ivanov@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Dimitri&backgroundColor=d1f4cc', 269.98, '2026-06-25', '2026-10-15', 'ACTIVE', 'Bulgaria'),
(20, 'Zara Khan', 'zara.khan@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Zara&backgroundColor=ffd5dc', 299.98, '2026-07-04', '2026-10-23', 'ACTIVE', 'Pakistan'),
(21, 'Noah Andersen', 'noah.andersen@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Noah&backgroundColor=ffdfbf', 249.98, '2026-07-18', '2026-10-22', 'ACTIVE', 'Denmark'),
(22, 'Isabella Rossi', 'isabella.rossi@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Isabella&backgroundColor=c0aede', 419.97, '2026-08-01', '2026-10-24', 'ACTIVE', 'Italy'),
(23, 'Alex Varga', 'alex.varga@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=AlexV&backgroundColor=b6e3f4', 249.98, '2026-08-14', '2026-10-17', 'ACTIVE', 'Hungary'),
(24, 'Mia Lindqvist', 'mia.lindqvist@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Mia&backgroundColor=d1f4cc', 369.97, '2026-08-28', '2026-10-21', 'ACTIVE', 'Sweden'),
(25, 'Samuel Mensah', 'samuel.mensah@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Samuel&backgroundColor=ffd5dc', 149.99, '2026-09-08', '2026-10-23', 'ACTIVE', 'Ghana'),
(26, 'Elena Popova', 'elena.popova@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=ElenaP&backgroundColor=ffdfbf', 309.97, '2026-09-18', '2026-10-22', 'ACTIVE', 'Poland'),
(27, 'Jackson Reed', 'jackson.reed@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Jackson&backgroundColor=c0aede', 449.97, '2026-09-28', '2026-10-24', 'ACTIVE', 'Australia'),
(28, 'Linh Nguyen', 'linh.nguyen@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Linh&backgroundColor=b6e3f4', 219.98, '2026-10-05', '2026-10-23', 'ACTIVE', 'Vietnam'),
(29, 'Tariq Mansour', 'tariq.mansour@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Tariq&backgroundColor=d1f4cc', 299.98, '2026-10-12', '2026-10-22', 'ACTIVE', 'Egypt'),
(30, 'Maya Cohen', 'maya.cohen@email.com', 'https://api.dicebear.com/9.x/avataaars/svg?seed=Maya&backgroundColor=ffd5dc', 249.98, '2026-10-19', '2026-10-24', 'ACTIVE', 'Israel')
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    avatar = EXCLUDED.avatar,
    total_spent = EXCLUDED.total_spent,
    joined_at = EXCLUDED.joined_at,
    last_active = EXCLUDED.last_active,
    status = EXCLUDED.status,
    country = EXCLUDED.country;

-- 6. Student Courses (Course Enrollment and Completion Tracking)
INSERT INTO student_courses (student_id, course_id, completed) VALUES
-- Student 1
(1, 1, true), (1, 3, true), (1, 5, false),
-- Student 2
(2, 1, false), (2, 2, true),
-- Student 3
(3, 1, true), (3, 2, true), (3, 4, true), (3, 5, false),
-- Student 4
(4, 2, false),
-- Student 5
(5, 1, true), (5, 4, true), (5, 5, false),
-- Student 6
(6, 1, true), (6, 2, true), (6, 5, true), (6, 6, false),
-- Student 7
(7, 3, true), (7, 4, true),
-- Student 8
(8, 1, false),
-- Student 9
(9, 2, true), (9, 4, true), (9, 5, false),
-- Student 10
(10, 1, true), (10, 3, false), (10, 6, false),
-- Student 11
(11, 2, true), (11, 6, false),
-- Student 12
(12, 1, true), (12, 3, true), (12, 4, true),
-- Student 13
(13, 2, true), (13, 3, false),
-- Student 14
(14, 1, true), (14, 2, false), (14, 4, true),
-- Student 15
(15, 1, true), (15, 2, true), (15, 4, false), (15, 5, true),
-- Student 16
(16, 2, true), (16, 4, true), (16, 5, false),
-- Student 17
(17, 6, false),
-- Student 18
(18, 1, true), (18, 4, true), (18, 5, false),
-- Student 19
(19, 2, true), (19, 6, false),
-- Student 20
(20, 1, true), (20, 5, true),
-- Student 21
(21, 2, true), (21, 4, false),
-- Student 22
(22, 1, true), (22, 5, false), (22, 6, true),
-- Student 23
(23, 1, true), (23, 6, false),
-- Student 24
(24, 2, true), (24, 4, false), (24, 6, false),
-- Student 25
(25, 2, true),
-- Student 26
(26, 1, true), (26, 3, false), (26, 4, false),
-- Student 27
(27, 1, false), (27, 2, true), (27, 5, true),
-- Student 28
(28, 3, false), (28, 8, false),
-- Student 29
(29, 1, false), (29, 5, false),
-- Student 30
(30, 3, false), (30, 5, false)
ON CONFLICT (student_id, course_id) DO UPDATE SET completed = EXCLUDED.completed;

-- 7. Enrollments (Full 1-year historical ledger from late 2025 through October 2026)
INSERT INTO enrollments (id, student_id, course_id, enrolled_at, amount) VALUES
-- October 2025
(1, 1, 1, '2025-10-18', 129.99),
-- November 2025
(2, 2, 2, '2025-11-03', 149.99),
(3, 3, 1, '2025-11-21', 129.99),
-- December 2025
(4, 4, 2, '2025-12-06', 149.99),
(5, 5, 1, '2025-12-19', 129.99),
(6, 1, 3, '2025-12-28', 79.99),
-- January 2026
(7, 6, 1, '2026-01-09', 129.99),
(8, 7, 3, '2026-01-23', 79.99),
(9, 3, 2, '2026-01-30', 149.99),
-- February 2026
(10, 8, 1, '2026-02-06', 129.99),
(11, 9, 2, '2026-02-19', 149.99),
(12, 6, 2, '2026-02-27', 149.99),
-- March 2026
(13, 10, 1, '2026-03-02', 129.99),
(14, 11, 2, '2026-03-15', 149.99),
(15, 12, 1, '2026-03-29', 129.99),
(16, 5, 4, '2026-03-31', 99.99),
-- April 2026
(17, 13, 2, '2026-04-10', 149.99),
(18, 14, 1, '2026-04-21', 129.99),
(19, 7, 4, '2026-04-26', 99.99),
(20, 9, 4, '2026-04-29', 99.99),
-- May 2026
(21, 15, 1, '2026-05-03', 129.99),
(22, 16, 2, '2026-05-16', 149.99),
(23, 17, 6, '2026-05-30', 119.99),
(24, 10, 3, '2026-05-31', 79.99),
-- June 2026
(25, 18, 1, '2026-06-12', 129.99),
(26, 19, 2, '2026-06-26', 149.99),
(27, 12, 3, '2026-06-29', 79.99),
(28, 3, 4, '2026-06-30', 99.99),
-- July 2026
(29, 20, 1, '2026-07-05', 129.99),
(30, 21, 2, '2026-07-19', 149.99),
(31, 14, 4, '2026-07-25', 99.99),
(32, 13, 3, '2026-07-28', 79.99),
(33, 2, 1, '2026-07-31', 129.99),
-- August 2026
(34, 22, 1, '2026-08-02', 129.99),
(35, 23, 1, '2026-08-15', 129.99),
(36, 24, 2, '2026-08-29', 149.99),
(37, 18, 4, '2026-08-30', 99.99),
(38, 15, 2, '2026-08-31', 149.99),
-- September 2026
(39, 25, 2, '2026-09-09', 149.99),
(40, 26, 1, '2026-09-19', 129.99),
(41, 27, 2, '2026-09-29', 149.99),
(42, 16, 4, '2026-09-29', 99.99),
(43, 6, 5, '2026-09-30', 169.99),
(44, 21, 4, '2026-09-30', 99.99),
-- October 2026 (Recent enrollments for dynamic dashboard stats)
(45, 1, 5, '2026-10-01', 169.99),
(46, 22, 6, '2026-10-02', 119.99),
(47, 10, 6, '2026-10-03', 119.99),
(48, 12, 4, '2026-10-04', 99.99),
(49, 28, 3, '2026-10-05', 79.99),
(50, 3, 5, '2026-10-06', 169.99),
(51, 23, 6, '2026-10-08', 119.99),
(52, 24, 4, '2026-10-10', 99.99),
(53, 29, 1, '2026-10-12', 129.99),
(54, 15, 5, '2026-10-14', 169.99),
(55, 28, 8, '2026-10-15', 139.99),
(56, 19, 6, '2026-10-16', 119.99),
(57, 14, 2, '2026-10-17', 149.99),
(58, 20, 5, '2026-10-18', 169.99),
(59, 30, 3, '2026-10-19', 79.99),
(60, 9, 5, '2026-10-20', 169.99),
(61, 6, 6, '2026-10-21', 119.99),
(62, 26, 4, '2026-10-21', 99.99),
(63, 27, 5, '2026-10-22', 169.99),
(64, 5, 5, '2026-10-22', 169.99),
(65, 29, 5, '2026-10-23', 169.99),
(66, 18, 5, '2026-10-23', 169.99),
(67, 30, 5, '2026-10-24', 169.99),
(68, 22, 5, '2026-10-24', 169.99),
(69, 27, 1, '2026-10-24', 129.99),
(70, 11, 6, '2026-10-24', 119.99),
(71, 15, 4, '2026-10-24', 99.99),
(72, 24, 6, '2026-10-24', 119.99),
(73, 26, 3, '2026-10-24', 79.99)
ON CONFLICT (id) DO UPDATE SET
    student_id = EXCLUDED.student_id,
    course_id = EXCLUDED.course_id,
    enrolled_at = EXCLUDED.enrolled_at,
    amount = EXCLUDED.amount;

-- 8. Users (Platform Instructors & Demo Accounts)
INSERT INTO users (
    id, first_name, last_name, email, password, role, provider, provider_id,
    image_url, enabled, failed_login_attempts, bio, website, twitter, created_at, updated_at
) VALUES
(
    'a1b2c3d4-e5f6-4a1b-8c2d-3e4f5a6b7c8d',
    'Alex',
    'Chen',
    'alex.chen@courseaurahub.io',
    '$2a$10$wO0oX.YI7N7Wq9aV02J.4ev4/4g4rWfX1p6LgQc5t7K2uM8v3xS8q',
    'ROLE_ADMIN',
    'LOCAL',
    NULL,
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&fit=crop&q=80',
    TRUE,
    0,
    'Lead Architect & Nuxt/DevOps educator with over a decade of software engineering leadership.',
    'https://courseaurahub.io',
    '@alexchen_dev',
    '2025-10-01 08:00:00+00',
    '2026-10-24 00:00:00+00'
),
(
    'b2c3d4e5-f6a1-4b2c-9d3e-4f5a6b7c8d9e',
    'Elena',
    'Rostova',
    'elena.rostova@courseaurahub.io',
    '$2a$10$wO0oX.YI7N7Wq9aV02J.4ev4/4g4rWfX1p6LgQc5t7K2uM8v3xS8q',
    'ROLE_USER',
    'LOCAL',
    NULL,
    'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=400&fit=crop&q=80',
    TRUE,
    0,
    'Principal Distributed Systems Engineer specializing in Spring Boot, Kafka, and Cloud Native Kubernetes.',
    'https://elenarostova.dev',
    '@elena_backend',
    '2025-10-15 09:30:00+00',
    '2026-10-24 00:00:00+00'
),
(
    'c3d4e5f6-a1b2-4c3d-ae4f-5a6b7c8d9e0f',
    'Sofia',
    'Park',
    'sofia.park@courseaurahub.io',
    '$2a$10$wO0oX.YI7N7Wq9aV02J.4ev4/4g4rWfX1p6LgQc5t7K2uM8v3xS8q',
    'ROLE_USER',
    'LOCAL',
    NULL,
    'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=400&fit=crop&q=80',
    TRUE,
    0,
    'Design Systems Director creating accessible, high-craft digital experiences with Figma & Tailwind.',
    'https://sofiapark.design',
    '@sofiapark_ui',
    '2025-11-01 10:00:00+00',
    '2026-10-24 00:00:00+00'
),
(
    'd4e5f6a1-b2c3-4d4e-bf5a-6b7c8d9e0f1a',
    'Marcus',
    'Thorne',
    'marcus.thorne@courseaurahub.io',
    '$2a$10$wO0oX.YI7N7Wq9aV02J.4ev4/4g4rWfX1p6LgQc5t7K2uM8v3xS8q',
    'ROLE_USER',
    'LOCAL',
    NULL,
    'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&fit=crop&q=80',
    TRUE,
    0,
    'AI Researcher & Systems Architect building multi-agent autonomous intelligence workflows.',
    'https://marcusthorne.ai',
    '@marcusthorne_ai',
    '2025-11-20 11:15:00+00',
    '2026-10-24 00:00:00+00'
)
ON CONFLICT (email) DO NOTHING;

-- 9. Update Identity Sequences so future inserts via JPA do not collide
SELECT setval(pg_get_serial_sequence('courses', 'id'), COALESCE((SELECT MAX(id) FROM courses), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('students', 'id'), COALESCE((SELECT MAX(id) FROM students), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('enrollments', 'id'), COALESCE((SELECT MAX(id) FROM enrollments), 0) + 1, false);

