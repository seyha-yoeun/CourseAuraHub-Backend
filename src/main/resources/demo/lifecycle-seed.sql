-- Full-lifecycle demo data, run by DemoLifecycleSeeder only when app.demo-data.enabled=true.
--
-- It builds on the catalog that migrations V0013, V0017 and V0036 seed, and adds sign-in-able accounts for every
-- role plus the data each part of the app needs: curriculum for every catalog course, instructor courses in each
-- review state, pacing, quiz rules, assignments, progress, certificates, Q&A, notes, reviews, carts, orders in every
-- status, coupons, sales, member cards, notifications, announcements, profiles, learning paths and an audit trail.
--
-- Every demo account uses one password, {{DEMO_PASSWORD_HASH}} below, which the seeder replaces with a fresh BCrypt
-- hash of app.demo-data.password. The whole script is skipped when admin@demo.courseaurahub.io already exists, and
-- each insert also skips rows that are already there, so running it twice changes nothing.

-- ---------------------------------------------------------------------------------------------------------------
-- Helpers (session-scoped, dropped when the connection closes)
-- ---------------------------------------------------------------------------------------------------------------

-- Random Crockford base32 code, the alphabet the app uses for order, card and certificate numbers
CREATE OR REPLACE FUNCTION pg_temp.demo_code(p_prefix text, p_groups int, p_length int, p_separator text DEFAULT '-')
RETURNS text LANGUAGE plpgsql AS $fn$
DECLARE
    v_alphabet constant text := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    v_code text := p_prefix;
BEGIN
    FOR g IN 1..p_groups LOOP
        v_code := v_code || p_separator;
        FOR i IN 1..p_length LOOP
            v_code := v_code || substr(v_alphabet, 1 + floor(random() * 32)::int, 1);
        END LOOP;
    END LOOP;
    RETURN v_code;
END
$fn$;

CREATE OR REPLACE FUNCTION pg_temp.demo_user(
    p_email text, p_first text, p_last text, p_role text, p_password text, p_created timestamptz,
    p_bio text DEFAULT NULL, p_image text DEFAULT NULL, p_verified boolean DEFAULT true, p_enabled boolean DEFAULT true
) RETURNS uuid LANGUAGE plpgsql AS $fn$
DECLARE
    v_id uuid;
BEGIN
    SELECT id INTO v_id FROM users WHERE lower(email) = lower(p_email);
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;
    INSERT INTO users (id, first_name, last_name, email, password, role, provider, enabled, failed_login_attempts,
                       email_verified, email_verified_at, mfa_enabled, token_version, bio, image_url, created_at, updated_at)
    VALUES (gen_random_uuid(), p_first, p_last, lower(p_email), p_password, p_role, 'LOCAL', p_enabled, 0,
            p_verified, CASE WHEN p_verified THEN p_created END, false, 0, p_bio,
            coalesce(p_image, 'https://api.dicebear.com/9.x/avataaars/svg?seed=' || p_first || p_last),
            p_created, p_created)
    RETURNING id INTO v_id;
    RETURN v_id;
END
$fn$;

-- The learner record behind an account, linked the way LearnerAccountService links it
CREATE OR REPLACE FUNCTION pg_temp.demo_student(p_user uuid, p_country text, p_joined date) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_id int;
    v_user users%ROWTYPE;
BEGIN
    SELECT id INTO v_id FROM students WHERE user_id = p_user;
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;
    SELECT * INTO v_user FROM users WHERE id = p_user;
    INSERT INTO students (name, email, avatar, total_spent, joined_at, last_active, status, country, user_id)
    VALUES (v_user.first_name || ' ' || v_user.last_name, v_user.email, v_user.image_url, 0, p_joined, p_joined,
            'ACTIVE', p_country, p_user)
    RETURNING id INTO v_id;
    RETURN v_id;
END
$fn$;

CREATE OR REPLACE FUNCTION pg_temp.demo_course_id(p_slug text) RETURNS int LANGUAGE sql AS $fn$
    SELECT id FROM courses WHERE slug = p_slug
$fn$;

CREATE OR REPLACE FUNCTION pg_temp.demo_lesson_id(p_slug text, p_title text) RETURNS int LANGUAGE sql AS $fn$
    SELECT l.id FROM lessons l JOIN course_sections s ON s.id = l.section_id JOIN courses c ON c.id = s.course_id
    WHERE c.slug = p_slug AND l.title = p_title
$fn$;

-- A course an instructor (or admin) created, with the twelve monthly metric rows every course has
CREATE OR REPLACE FUNCTION pg_temp.demo_course(
    p_slug text, p_title text, p_description text, p_instructor text, p_owner uuid, p_price numeric,
    p_original numeric, p_status text, p_category text, p_level text, p_thumbnail text, p_created date,
    p_tags text[], p_review_note text DEFAULT NULL, p_sequential boolean DEFAULT false
) RETURNS int LANGUAGE plpgsql AS $fn$
DECLARE
    v_id int;
BEGIN
    SELECT id INTO v_id FROM courses WHERE slug = p_slug;
    IF v_id IS NOT NULL THEN
        RETURN v_id;
    END IF;
    INSERT INTO courses (slug, title, description, instructor, price, original_price, status, category, level,
                         thumbnail, rating, total_students, total_lessons, total_hours, revenue, completion_rate,
                         created_at, updated_at, owner_id, review_note, sequential_lessons)
    VALUES (p_slug, p_title, p_description, p_instructor, p_price, p_original, p_status, p_category, p_level,
            p_thumbnail, 0, 0, 0, 0, 0, 0, p_created, p_created, p_owner, p_review_note, p_sequential)
    RETURNING id INTO v_id;
    INSERT INTO course_monthly_metrics (course_id, month, enrollments, revenue)
    SELECT v_id, m, 0, 0 FROM generate_series(1, 12) AS m;
    INSERT INTO course_tags (course_id, tag_order, tag)
    SELECT v_id, t.ordinality - 1, t.tag FROM unnest(p_tags) WITH ORDINALITY AS t(tag, ordinality);
    RETURN v_id;
END
$fn$;

-- Adds a curriculum from JSON only to a course that has none:
-- [{"title": "...", "lessons": [{"title", "type", "minutes", "preview", "releaseAfterDays"}]}]
CREATE OR REPLACE FUNCTION pg_temp.demo_curriculum(p_course int, p_url_base text, p_sections jsonb) RETURNS void
LANGUAGE plpgsql AS $fn$
DECLARE
    v_section jsonb;
    v_lesson jsonb;
    v_section_id int;
    v_section_pos int := 0;
    v_lesson_pos int;
BEGIN
    IF p_course IS NULL OR EXISTS (SELECT 1 FROM course_sections WHERE course_id = p_course) THEN
        RETURN;
    END IF;
    FOR v_section IN SELECT * FROM jsonb_array_elements(p_sections) LOOP
        INSERT INTO course_sections (course_id, title, position)
        VALUES (p_course, v_section ->> 'title', v_section_pos)
        RETURNING id INTO v_section_id;
        v_lesson_pos := 0;
        FOR v_lesson IN SELECT * FROM jsonb_array_elements(v_section -> 'lessons') LOOP
            INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position,
                                 release_after_days)
            VALUES (v_section_id, v_lesson ->> 'title', v_lesson ->> 'type', (v_lesson ->> 'minutes')::int,
                    p_url_base || '/' || (v_section_pos + 1) || '-' || (v_lesson_pos + 1)
                        || CASE WHEN v_lesson ->> 'type' = 'VIDEO' THEN '.mp4' ELSE '' END,
                    coalesce((v_lesson ->> 'preview')::boolean, false), v_lesson_pos,
                    (v_lesson ->> 'releaseAfterDays')::int);
            v_lesson_pos := v_lesson_pos + 1;
        END LOOP;
        v_section_pos := v_section_pos + 1;
    END LOOP;
    -- The same totals CurriculumService keeps after a lesson changes
    UPDATE courses c
    SET total_lessons = t.lessons, total_hours = round(t.minutes / 60.0, 2)
    FROM (SELECT count(*) AS lessons, coalesce(sum(l.duration_minutes), 0) AS minutes
          FROM lessons l JOIN course_sections s ON s.id = l.section_id WHERE s.course_id = p_course) t
    WHERE c.id = p_course;
END
$fn$;

-- Questions for a quiz lesson that has none: [{"prompt", "explanation", "options": [...], "correct": index}]
CREATE OR REPLACE FUNCTION pg_temp.demo_quiz(p_lesson int, p_questions jsonb) RETURNS void
LANGUAGE plpgsql AS $fn$
DECLARE
    v_question jsonb;
    v_question_id int;
    v_position int := 0;
BEGIN
    IF p_lesson IS NULL OR EXISTS (SELECT 1 FROM quiz_questions WHERE lesson_id = p_lesson) THEN
        RETURN;
    END IF;
    FOR v_question IN SELECT * FROM jsonb_array_elements(p_questions) LOOP
        INSERT INTO quiz_questions (lesson_id, position, prompt, explanation)
        VALUES (p_lesson, v_position, v_question ->> 'prompt', v_question ->> 'explanation')
        RETURNING id INTO v_question_id;
        INSERT INTO quiz_options (question_id, position, text, correct)
        SELECT v_question_id, o.ordinality - 1, o.value, o.ordinality - 1 = (v_question ->> 'correct')::int
        FROM jsonb_array_elements_text(v_question -> 'options') WITH ORDINALITY AS o;
        v_position := v_position + 1;
    END LOOP;
END
$fn$;

-- Enrolls a learner the way EnrollmentService does: the enrollment ledger row, the course link, and the counters
-- (course students, revenue and month, learner spending). A paid enrollment gets its PAID order.
CREATE OR REPLACE FUNCTION pg_temp.demo_enroll(
    p_student int, p_course int, p_on date, p_list numeric DEFAULT NULL, p_coupon text DEFAULT NULL,
    p_provider text DEFAULT 'fake'
) RETURNS text LANGUAGE plpgsql AS $fn$
DECLARE
    v_list numeric;
    v_amount numeric;
    v_coupon coupons%ROWTYPE;
    v_order text;
BEGIN
    IF p_student IS NULL OR p_course IS NULL
       OR EXISTS (SELECT 1 FROM student_courses WHERE student_id = p_student AND course_id = p_course) THEN
        RETURN NULL;
    END IF;
    v_list := coalesce(p_list, (SELECT price FROM courses WHERE id = p_course));
    v_amount := v_list;
    IF p_coupon IS NOT NULL THEN
        SELECT * INTO v_coupon FROM coupons WHERE code = p_coupon;
        v_amount := greatest(0, round(CASE v_coupon.discount_type
            WHEN 'PERCENT' THEN v_list * (1 - v_coupon.discount_value / 100)
            ELSE v_list - v_coupon.discount_value END, 2));
    END IF;

    INSERT INTO student_courses (student_id, course_id, completed) VALUES (p_student, p_course, false);
    INSERT INTO enrollments (student_id, course_id, enrolled_at, amount) VALUES (p_student, p_course, p_on, v_amount);
    IF v_list > 0 THEN
        v_order := pg_temp.demo_code('ORD', 1, 16);
        INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency,
                            status, provider, provider_reference, coupon_id, created_at, paid_at)
        VALUES (v_order, p_student, p_course, v_list, v_list - v_amount, v_amount, 'USD', 'PAID', p_provider,
                CASE WHEN p_provider = 'fake' THEN 'fake_' || lower(substr(v_order, 5)) END, v_coupon.id,
                p_on::timestamptz + interval '10 hours', p_on::timestamptz + interval '10 hours 2 minutes');
    END IF;

    UPDATE courses SET total_students = total_students + 1, revenue = revenue + v_amount WHERE id = p_course;
    UPDATE course_monthly_metrics SET enrollments = enrollments + 1, revenue = revenue + v_amount
    WHERE course_id = p_course AND month = extract(month FROM p_on);
    UPDATE students SET total_spent = total_spent + v_amount, last_active = greatest(last_active, p_on)
    WHERE id = p_student;
    -- An enrolled course leaves the wishlist, as WishlistService.removeIfPresent does
    DELETE FROM wishlist_items WHERE student_id = p_student AND course_id = p_course;
    RETURN v_order;
END
$fn$;

-- Completes the first p_count lessons in curriculum order, a day apart from p_start. A quiz lesson with questions
-- gets the passing attempt the app requires. Finishing every lesson completes the course and issues its permanent
-- certificate with the copied name, title and instructor.
CREATE OR REPLACE FUNCTION pg_temp.demo_progress(p_student int, p_course int, p_count int, p_start timestamptz)
RETURNS void LANGUAGE plpgsql AS $fn$
DECLARE
    v_lesson record;
    v_done int := 0;
    v_total int;
    v_at timestamptz;
    v_questions int;
BEGIN
    IF p_student IS NULL OR p_course IS NULL THEN
        RETURN;
    END IF;
    FOR v_lesson IN
        SELECT l.id, l.type FROM lessons l JOIN course_sections s ON s.id = l.section_id
        WHERE s.course_id = p_course ORDER BY s.position, s.id, l.position, l.id LIMIT p_count
    LOOP
        v_at := p_start + make_interval(days => v_done, hours => 19);
        SELECT count(*) INTO v_questions FROM quiz_questions WHERE lesson_id = v_lesson.id;
        IF v_lesson.type = 'QUIZ' AND v_questions > 0
           AND NOT EXISTS (SELECT 1 FROM quiz_attempts WHERE student_id = p_student AND lesson_id = v_lesson.id AND passed) THEN
            INSERT INTO quiz_attempts (student_id, lesson_id, correct_count, total_count, score_percent, passed, submitted_at)
            VALUES (p_student, v_lesson.id, v_questions, v_questions, 100, true, v_at - interval '5 minutes');
        END IF;
        INSERT INTO lesson_progress (student_id, lesson_id, completed_at) VALUES (p_student, v_lesson.id, v_at)
        ON CONFLICT DO NOTHING;
        v_done := v_done + 1;
    END LOOP;

    UPDATE students SET last_active = greatest(last_active, coalesce(v_at, p_start)::date) WHERE id = p_student;

    SELECT count(*) INTO v_total FROM lessons l JOIN course_sections s ON s.id = l.section_id WHERE s.course_id = p_course;
    IF v_total > 0 AND (SELECT count(*) FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id
                        JOIN course_sections s ON s.id = l.section_id
                        WHERE lp.student_id = p_student AND s.course_id = p_course) >= v_total THEN
        UPDATE student_courses SET completed = true WHERE student_id = p_student AND course_id = p_course;
        INSERT INTO course_certificates (certificate_number, student_id, course_id, issued_at, learner_name, course_title, instructor)
        SELECT pg_temp.demo_code('CAH', 4, 4), p_student, c.id, v_at, st.name, c.title, c.instructor
        FROM courses c, students st WHERE c.id = p_course AND st.id = p_student
        ON CONFLICT DO NOTHING;
    END IF;
END
$fn$;

-- Course completion rate as the share of enrolled learners who finished, as the progress service keeps it
CREATE OR REPLACE FUNCTION pg_temp.demo_refresh_completion(p_course int) RETURNS void LANGUAGE sql AS $fn$
    UPDATE courses c
    SET completion_rate = coalesce((SELECT round(100.0 * count(*) FILTER (WHERE completed) / nullif(count(*), 0), 2)
                                    FROM student_courses WHERE course_id = c.id), 0)
    WHERE c.id = p_course AND c.owner_id IS NOT NULL
$fn$;

CREATE OR REPLACE FUNCTION pg_temp.demo_notify(p_user uuid, p_type text, p_title text, p_message text, p_link text,
                                               p_at timestamptz, p_read boolean) RETURNS void LANGUAGE sql AS $fn$
    INSERT INTO notifications (user_id, type, title, message, link, created_at, read_at)
    SELECT p_user, p_type, p_title, p_message, p_link, p_at, CASE WHEN p_read THEN p_at + interval '2 hours' END
    WHERE p_user IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM notifications WHERE user_id = p_user AND type = p_type AND title = p_title
                                                    AND coalesce(link, '') = coalesce(p_link, ''))
$fn$;

-- ---------------------------------------------------------------------------------------------------------------
-- The seed
-- ---------------------------------------------------------------------------------------------------------------
DO $seed$
DECLARE
    v_password constant text := '{{DEMO_PASSWORD_HASH}}';
    v_today constant date := current_date;
    v_now constant timestamptz := now();

    -- Accounts
    u_admin uuid; u_sokha uuid; u_lina uuid;
    u_vannak uuid; u_sreymom uuid; u_rithy uuid; u_nita uuid; u_bopha uuid;
    u_kosal uuid; u_malis uuid; u_piseth uuid; u_chantha uuid; u_dalin uuid; u_sophea uuid;
    s_vannak int; s_sreymom int; s_rithy int; s_nita int; s_bopha int;
    s_kosal int; s_malis int; s_piseth int; s_chantha int; s_dalin int; s_sophea int;

    -- Courses
    c_git int; c_nuxt int; c_spring int; c_ts int; c_tailwind int; c_ai int; c_docker int; c_flutter int; c_cloud int;
    c_python int; c_ml int; c_sql int; c_ux int; c_figma int;

    -- Lessons, cards, ids reused below
    l_py_quiz int; l_py_project int; l_py_intro int; l_py_pandas int; l_nuxt_quiz int; l_ux_quiz int;
    v_assignment int; v_question int; v_card int; v_topup int; v_group int; v_path int; v_order text;
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE email = 'admin@demo.courseaurahub.io') THEN
        RAISE NOTICE 'Lifecycle demo data already present; skipping';
        RETURN;
    END IF;

    -- =====================================================================================================
    -- 1. Accounts for every role and account state
    -- =====================================================================================================
    u_admin := pg_temp.demo_user('admin@demo.courseaurahub.io', 'Dara', 'Sok', 'ROLE_ADMIN', v_password,
        v_now - interval '400 days', 'Runs the CourseAuraHub platform: catalog, payments and learner support.');
    u_sokha := pg_temp.demo_user('sokha.chan@demo.courseaurahub.io', 'Sokha', 'Chan', 'ROLE_INSTRUCTOR', v_password,
        v_now - interval '300 days', 'Data analyst turned teacher. Ten years of Python, SQL and dashboards for NGOs and banks in Phnom Penh.');
    u_lina := pg_temp.demo_user('lina.meas@demo.courseaurahub.io', 'Lina', 'Meas', 'ROLE_INSTRUCTOR', v_password,
        v_now - interval '250 days', 'Product designer and UX researcher. I help teams talk to users before they build.');

    -- Learners, each at a different point of the learner journey
    u_vannak := pg_temp.demo_user('vannak.kim@demo.courseaurahub.io', 'Vannak', 'Kim', 'ROLE_USER', v_password, v_now - interval '120 days',
        'Junior web developer learning the full stack.');
    u_sreymom := pg_temp.demo_user('sreymom.chea@demo.courseaurahub.io', 'Sreymom', 'Chea', 'ROLE_USER', v_password, v_now - interval '200 days',
        'Frontend developer. Finished the Frontend Developer path.');
    u_rithy := pg_temp.demo_user('rithy.pov@demo.courseaurahub.io', 'Rithy', 'Pov', 'ROLE_USER', v_password, v_now - interval '60 days');
    u_nita := pg_temp.demo_user('nita.ouk@demo.courseaurahub.io', 'Nita', 'Ouk', 'ROLE_USER', v_password, v_now - interval '45 days');
    u_bopha := pg_temp.demo_user('bopha.lim@demo.courseaurahub.io', 'Bopha', 'Lim', 'ROLE_USER', v_password, v_now - interval '1 day');
    -- A cohort for the instructor's analytics funnel
    u_kosal := pg_temp.demo_user('kosal.heng@demo.courseaurahub.io', 'Kosal', 'Heng', 'ROLE_USER', v_password, v_now - interval '90 days');
    u_malis := pg_temp.demo_user('malis.tep@demo.courseaurahub.io', 'Malis', 'Tep', 'ROLE_USER', v_password, v_now - interval '80 days');
    u_piseth := pg_temp.demo_user('piseth.ros@demo.courseaurahub.io', 'Piseth', 'Ros', 'ROLE_USER', v_password, v_now - interval '70 days');
    u_chantha := pg_temp.demo_user('chantha.mao@demo.courseaurahub.io', 'Chantha', 'Mao', 'ROLE_USER', v_password, v_now - interval '65 days');
    u_dalin := pg_temp.demo_user('dalin.yim@demo.courseaurahub.io', 'Dalin', 'Yim', 'ROLE_USER', v_password, v_now - interval '50 days');
    u_sophea := pg_temp.demo_user('sophea.nhem@demo.courseaurahub.io', 'Sophea', 'Nhem', 'ROLE_USER', v_password, v_now - interval '40 days');
    -- Signed up but never verified the emailed code, and an account an admin disabled
    PERFORM pg_temp.demo_user('pending.signup@demo.courseaurahub.io', 'Pending', 'Signup', 'ROLE_USER', v_password,
        v_now - interval '2 hours', NULL, NULL, false, true);
    PERFORM pg_temp.demo_user('disabled.account@demo.courseaurahub.io', 'Disabled', 'Account', 'ROLE_USER', v_password,
        v_now - interval '150 days', NULL, NULL, true, false);

    s_vannak := pg_temp.demo_student(u_vannak, 'Cambodia', (v_now - interval '120 days')::date);
    s_sreymom := pg_temp.demo_student(u_sreymom, 'Cambodia', (v_now - interval '200 days')::date);
    s_rithy := pg_temp.demo_student(u_rithy, 'Cambodia', (v_now - interval '60 days')::date);
    s_nita := pg_temp.demo_student(u_nita, 'Thailand', (v_now - interval '45 days')::date);
    s_bopha := pg_temp.demo_student(u_bopha, 'Cambodia', (v_now - interval '1 day')::date);
    s_kosal := pg_temp.demo_student(u_kosal, 'Cambodia', (v_now - interval '90 days')::date);
    s_malis := pg_temp.demo_student(u_malis, 'Vietnam', (v_now - interval '80 days')::date);
    s_piseth := pg_temp.demo_student(u_piseth, 'Cambodia', (v_now - interval '70 days')::date);
    s_chantha := pg_temp.demo_student(u_chantha, 'Laos', (v_now - interval '65 days')::date);
    s_dalin := pg_temp.demo_student(u_dalin, 'Cambodia', (v_now - interval '50 days')::date);
    s_sophea := pg_temp.demo_student(u_sophea, 'Cambodia', (v_now - interval '40 days')::date);

    -- =====================================================================================================
    -- 2. Curriculum for every catalog course that has none yet
    -- =====================================================================================================
    c_git := pg_temp.demo_course_id('git-github-fundamentals');
    c_nuxt := pg_temp.demo_course_id('nuxt-3-mastery');
    c_spring := pg_temp.demo_course_id('spring-boot-microservices');
    c_ts := pg_temp.demo_course_id('typescript-for-vue-devs');
    c_tailwind := pg_temp.demo_course_id('tailwind-ui-design-system');
    c_ai := pg_temp.demo_course_id('ai-agents-llm-engineering');
    c_docker := pg_temp.demo_course_id('docker-kubernetes-devops');
    c_flutter := pg_temp.demo_course_id('flutter-crossplatform-mobile');
    c_cloud := pg_temp.demo_course_id('cloud-security-and-devsecops');

    PERFORM pg_temp.demo_curriculum(c_ts, 'https://cdn.courseaurahub.io/typescript', $j$[
      {"title": "Types for Vue developers", "lessons": [
        {"title": "Why TypeScript in a Vue project", "type": "VIDEO", "minutes": 9, "preview": true},
        {"title": "Typing props, emits and refs", "type": "VIDEO", "minutes": 24},
        {"title": "Generics without the fear", "type": "ARTICLE", "minutes": 15}]},
      {"title": "Typed applications", "lessons": [
        {"title": "Typing Pinia stores", "type": "VIDEO", "minutes": 21},
        {"title": "Typed API clients", "type": "VIDEO", "minutes": 26},
        {"title": "TypeScript check-up", "type": "QUIZ", "minutes": 8}]}]$j$);
    PERFORM pg_temp.demo_curriculum(c_tailwind, 'https://cdn.courseaurahub.io/tailwind', $j$[
      {"title": "Design tokens", "lessons": [
        {"title": "From Figma to tokens", "type": "VIDEO", "minutes": 12, "preview": true},
        {"title": "Color, type and spacing scales", "type": "VIDEO", "minutes": 25}]},
      {"title": "Components", "lessons": [
        {"title": "Buttons, inputs and states", "type": "VIDEO", "minutes": 30},
        {"title": "Accessible dialogs and menus", "type": "ARTICLE", "minutes": 18},
        {"title": "Dark mode that holds up", "type": "VIDEO", "minutes": 20}]}]$j$);
    PERFORM pg_temp.demo_curriculum(c_ai, 'https://cdn.courseaurahub.io/ai-agents', $j$[
      {"title": "LLM foundations", "lessons": [
        {"title": "How large language models work", "type": "VIDEO", "minutes": 18, "preview": true},
        {"title": "Prompting that survives production", "type": "ARTICLE", "minutes": 16}]},
      {"title": "Agents", "lessons": [
        {"title": "Tool calling and function schemas", "type": "VIDEO", "minutes": 34},
        {"title": "Retrieval-augmented generation", "type": "VIDEO", "minutes": 38},
        {"title": "Evaluating agents", "type": "VIDEO", "minutes": 27}]}]$j$);
    PERFORM pg_temp.demo_curriculum(c_docker, 'https://cdn.courseaurahub.io/devops', $j$[
      {"title": "Containers", "lessons": [
        {"title": "Images, layers and registries", "type": "VIDEO", "minutes": 16, "preview": true},
        {"title": "Writing a production Dockerfile", "type": "VIDEO", "minutes": 28}]},
      {"title": "Kubernetes", "lessons": [
        {"title": "Pods, deployments and services", "type": "VIDEO", "minutes": 35},
        {"title": "Config, secrets and probes", "type": "ARTICLE", "minutes": 20},
        {"title": "Rolling out with Helm", "type": "VIDEO", "minutes": 31}]}]$j$);
    PERFORM pg_temp.demo_curriculum(c_flutter, 'https://cdn.courseaurahub.io/flutter', $j$[
      {"title": "Getting started", "lessons": [
        {"title": "Dart in one hour", "type": "VIDEO", "minutes": 60},
        {"title": "Widgets and layout", "type": "VIDEO", "minutes": 32}]}]$j$);
    PERFORM pg_temp.demo_curriculum(c_cloud, 'https://cdn.courseaurahub.io/devsecops', $j$[
      {"title": "Threat modelling", "lessons": [
        {"title": "Thinking like an attacker", "type": "VIDEO", "minutes": 22, "preview": true},
        {"title": "Securing the CI pipeline", "type": "VIDEO", "minutes": 29}]}]$j$);

    PERFORM pg_temp.demo_quiz(pg_temp.demo_lesson_id('typescript-for-vue-devs', 'TypeScript check-up'), $j$[
      {"prompt": "How do you type a component's props with the Composition API?", "explanation": "defineProps with a type argument gives typed props without runtime declarations.",
       "options": ["defineProps<{ title: string }>()", "props: any", "useProps()", "this.$props"], "correct": 0},
      {"prompt": "What does a generic type parameter let a function do?", "explanation": "Generics keep the relationship between input and output types.",
       "options": ["Run faster", "Work with many types while staying type-safe", "Skip type checking", "Import modules"], "correct": 1},
      {"prompt": "Which file configures the TypeScript compiler?", "explanation": "tsconfig.json holds the compiler options.",
       "options": ["package.json", "vite.config.ts", "tsconfig.json", ".eslintrc"], "correct": 2}]$j$);

    -- =====================================================================================================
    -- 3. Instructor courses, one per review state
    -- =====================================================================================================
    -- Published through review: sequential lessons, a dripped bonus lesson, a quiz with every rule, a required assignment
    c_python := pg_temp.demo_course('python-for-data-analysis', 'Python for Data Analysis',
        'Go from spreadsheets to pandas. Clean real datasets, analyse them and present what you found, one step at a time.',
        'Sokha Chan', u_sokha, 59.99, 89.99, 'PUBLISHED', 'Programming', 'BEGINNER',
        'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&h=450&fit=crop', (v_today - 150),
        ARRAY['python', 'pandas', 'data analysis'], NULL, true);
    PERFORM pg_temp.demo_curriculum(c_python, 'https://cdn.courseaurahub.io/python', $j$[
      {"title": "Python basics", "lessons": [
        {"title": "Welcome and setup", "type": "VIDEO", "minutes": 8, "preview": true},
        {"title": "Variables, lists and loops", "type": "VIDEO", "minutes": 22},
        {"title": "Functions you will reuse", "type": "ARTICLE", "minutes": 12}]},
      {"title": "Working with data", "lessons": [
        {"title": "Loading data with pandas", "type": "VIDEO", "minutes": 26},
        {"title": "Cleaning messy data", "type": "VIDEO", "minutes": 31},
        {"title": "Data analysis quiz", "type": "QUIZ", "minutes": 15}]},
      {"title": "Project", "lessons": [
        {"title": "Project: clean and analyse a dataset", "type": "ARTICLE", "minutes": 45},
        {"title": "Bonus: automating reports", "type": "VIDEO", "minutes": 18, "releaseAfterDays": 14}]}]$j$);
    l_py_intro := pg_temp.demo_lesson_id('python-for-data-analysis', 'Welcome and setup');
    l_py_pandas := pg_temp.demo_lesson_id('python-for-data-analysis', 'Loading data with pandas');
    l_py_quiz := pg_temp.demo_lesson_id('python-for-data-analysis', 'Data analysis quiz');
    l_py_project := pg_temp.demo_lesson_id('python-for-data-analysis', 'Project: clean and analyse a dataset');
    PERFORM pg_temp.demo_quiz(l_py_quiz, $j$[
      {"prompt": "Which pandas function reads a CSV file?", "explanation": "pd.read_csv loads a CSV into a DataFrame.",
       "options": ["pd.read_csv", "pd.open", "pd.load_table", "csv.read"], "correct": 0},
      {"prompt": "How do you drop rows with missing values?", "explanation": "dropna removes rows (or columns) that contain NaN.",
       "options": ["df.remove_na()", "df.dropna()", "df.clean()", "df.fillna(drop=True)"], "correct": 1},
      {"prompt": "What does df.groupby('city').size() return?", "explanation": "It counts rows per group.",
       "options": ["The column names", "A new DataFrame of cities", "The number of rows per city", "The first row of each city"], "correct": 2},
      {"prompt": "Which method shows the first five rows?", "explanation": "head() returns the first n rows, five by default.",
       "options": ["df.first()", "df.top()", "df.preview()", "df.head()"], "correct": 3},
      {"prompt": "Which type holds a single column of a DataFrame?", "explanation": "A column is a Series.",
       "options": ["Series", "List", "Index", "Panel"], "correct": 0}]$j$);
    -- Three questions drawn from five, shuffled, fifteen minutes, three attempts, half an hour between failures
    UPDATE lessons SET quiz_pass_percent = 60, quiz_max_attempts = 3, quiz_retry_cooldown_minutes = 30,
                       quiz_time_limit_minutes = 15, quiz_questions_per_attempt = 3, quiz_shuffle = true
    WHERE id = l_py_quiz;
    INSERT INTO assignments (lesson_id, instructions, pass_score, required, created_at, updated_at)
    VALUES (l_py_project,
            'Download the sales dataset from the lesson, remove duplicates and missing values, then answer: which month had the highest revenue and why? Submit a short write-up or your notebook as a PDF.',
            60, true, v_now - interval '140 days', v_now - interval '140 days')
    ON CONFLICT (lesson_id) DO NOTHING;
    SELECT id INTO v_assignment FROM assignments WHERE lesson_id = l_py_project;

    -- Sent for review and waiting for an admin
    c_ml := pg_temp.demo_course('machine-learning-foundations', 'Machine Learning Foundations',
        'Regression, classification and model evaluation with scikit-learn, explained with small real datasets.',
        'Sokha Chan', u_sokha, 79.99, 79.99, 'PENDING_REVIEW', 'AI & Machine Learning', 'INTERMEDIATE',
        'https://images.unsplash.com/photo-1555949963-aa79dcee981c?w=800&h=450&fit=crop', (v_today - 12),
        ARRAY['machine learning', 'scikit-learn']);
    PERFORM pg_temp.demo_curriculum(c_ml, 'https://cdn.courseaurahub.io/ml', $j$[
      {"title": "Supervised learning", "lessons": [
        {"title": "What a model learns", "type": "VIDEO", "minutes": 14, "preview": true},
        {"title": "Linear regression by hand", "type": "VIDEO", "minutes": 29},
        {"title": "Measuring a model", "type": "ARTICLE", "minutes": 17}]}]$j$);

    -- Sent back by an admin with changes requested
    c_sql := pg_temp.demo_course('sql-for-analysts', 'SQL for Analysts',
        'Write the queries analysts write every day: joins, grouping, window functions and clean reporting views.',
        'Sokha Chan', u_sokha, 49.99, 49.99, 'DRAFT', 'Programming', 'BEGINNER',
        'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=800&h=450&fit=crop', (v_today - 30),
        ARRAY['sql', 'postgresql'],
        'Great start. Please add a short quiz to the last section and a free preview lesson so learners can try it before buying.');
    PERFORM pg_temp.demo_curriculum(c_sql, 'https://cdn.courseaurahub.io/sql', $j$[
      {"title": "Querying", "lessons": [
        {"title": "SELECT, WHERE and ORDER BY", "type": "VIDEO", "minutes": 18},
        {"title": "Joins without tears", "type": "VIDEO", "minutes": 27},
        {"title": "GROUP BY and HAVING", "type": "ARTICLE", "minutes": 14}]}]$j$);

    -- Published by a second instructor, on sale, with a quiz anyone can retake
    c_ux := pg_temp.demo_course('ux-research-essentials', 'UX Research Essentials',
        'Plan interviews, run usability tests and turn what you hear into product decisions your team trusts.',
        'Lina Meas', u_lina, 39.99, 59.99, 'PUBLISHED', 'UI/UX Design', 'BEGINNER',
        'https://images.unsplash.com/photo-1586717791821-3f44a563fa4c?w=800&h=450&fit=crop', (v_today - 200),
        ARRAY['ux', 'research', 'usability']);
    PERFORM pg_temp.demo_curriculum(c_ux, 'https://cdn.courseaurahub.io/ux', $j$[
      {"title": "Research basics", "lessons": [
        {"title": "Why research before design", "type": "VIDEO", "minutes": 10, "preview": true},
        {"title": "Writing an interview guide", "type": "ARTICLE", "minutes": 14},
        {"title": "Running a usability test", "type": "VIDEO", "minutes": 24}]},
      {"title": "Making sense of it", "lessons": [
        {"title": "Affinity mapping", "type": "VIDEO", "minutes": 19},
        {"title": "Research check", "type": "QUIZ", "minutes": 6}]}]$j$);
    l_ux_quiz := pg_temp.demo_lesson_id('ux-research-essentials', 'Research check');
    PERFORM pg_temp.demo_quiz(l_ux_quiz, $j$[
      {"prompt": "How many users usually reveal most usability problems in one round?", "explanation": "Around five users find most issues in a single round of testing.",
       "options": ["About five", "At least fifty", "Exactly one", "Every user you have"], "correct": 0},
      {"prompt": "What should an interview question avoid?", "explanation": "Leading questions push people towards the answer you expect.",
       "options": ["Open wording", "Leading the participant", "Follow-up questions", "Silence"], "correct": 1}]$j$);

    -- A brand-new draft with nothing in it yet
    c_figma := pg_temp.demo_course('figma-prototyping-masterclass', 'Figma Prototyping Masterclass',
        'Interactive prototypes with variables, conditional logic and smart animate.',
        'Lina Meas', u_lina, 49.99, 49.99, 'DRAFT', 'UI/UX Design', 'INTERMEDIATE',
        'https://images.unsplash.com/photo-1609921212029-bb5a28e60960?w=800&h=450&fit=crop', (v_today - 2),
        ARRAY['figma', 'prototyping']);

    INSERT INTO instructor_profiles (user_id, handle, headline, linkedin_url, github_url, youtube_url, created_at, updated_at)
    VALUES (u_sokha, 'sokha-chan', 'Data analyst and Python teacher', 'https://www.linkedin.com/in/sokha-chan-demo',
            'https://github.com/sokha-chan-demo', 'https://www.youtube.com/@sokha-chan-demo', v_now - interval '160 days', v_now - interval '20 days'),
           (u_lina, 'lina-meas', 'Product designer and UX researcher', 'https://www.linkedin.com/in/lina-meas-demo',
            NULL, NULL, v_now - interval '210 days', v_now - interval '60 days')
    ON CONFLICT DO NOTHING;
    UPDATE users SET website = 'https://sokha.example.com', twitter = '@sokha_data' WHERE id = u_sokha AND website IS NULL;

    -- =====================================================================================================
    -- 4. Promotions: coupons and sales in every state
    -- =====================================================================================================
    INSERT INTO coupons (code, discount_type, discount_value, course_id, max_redemptions, starts_at, expires_at, active, created_at)
    VALUES ('PYTHON15', 'PERCENT', 15, c_python, NULL, NULL, NULL, true, v_now - interval '100 days'),
           ('LAUNCH50', 'PERCENT', 50, NULL, 1, NULL, NULL, true, v_now - interval '95 days'),
           ('SUMMER20', 'PERCENT', 20, NULL, NULL, v_now - interval '160 days', v_now - interval '100 days', true, v_now - interval '170 days'),
           ('NEWYEAR30', 'PERCENT', 30, NULL, 500, v_now + interval '30 days', v_now + interval '45 days', true, v_now - interval '1 day'),
           ('PAUSED10', 'FIXED', 10, NULL, NULL, NULL, NULL, false, v_now - interval '20 days')
    ON CONFLICT (code) DO NOTHING;

    INSERT INTO course_discounts (course_id, discount_type, discount_value, starts_at, ends_at, active, created_at)
    SELECT * FROM (VALUES
        (c_ux, 'PERCENT', 30::numeric, v_now - interval '5 days', v_now + interval '20 days', true, v_now - interval '6 days'),
        (c_ai, 'PERCENT', 25::numeric, v_now + interval '7 days', v_now + interval '14 days', true, v_now - interval '1 day'),
        (c_docker, 'FIXED', 20::numeric, v_now - interval '40 days', v_now - interval '10 days', true, v_now - interval '41 days'),
        (c_tailwind, 'PERCENT', 15::numeric, v_now - interval '3 days', NULL::timestamptz, false, v_now - interval '4 days')
    ) AS d(course_id, discount_type, discount_value, starts_at, ends_at, active, created_at)
    WHERE d.course_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM course_discounts x WHERE x.course_id = d.course_id AND x.starts_at::date = d.starts_at::date);

    -- =====================================================================================================
    -- 5. Learner journeys
    -- =====================================================================================================

    -- Vannak: an active learner part-way through three courses, paying by card and by member card
    PERFORM pg_temp.demo_enroll(s_vannak, c_git, v_today - 100);
    PERFORM pg_temp.demo_progress(s_vannak, c_git, 3, v_now - interval '99 days');
    PERFORM pg_temp.demo_enroll(s_vannak, c_nuxt, v_today - 90, NULL, 'WELCOME10');
    PERFORM pg_temp.demo_progress(s_vannak, c_nuxt, 4, v_now - interval '89 days');
    INSERT INTO lesson_watch_positions (student_id, lesson_id, position_seconds, updated_at)
    VALUES (s_vannak, pg_temp.demo_lesson_id('nuxt-3-mastery', 'Data fetching with useFetch'), 640, v_now - interval '9 days')
    ON CONFLICT DO NOTHING;

    -- Member card: application approved, a 200 top-up approved, then the Python course bought with the card
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, reviewed_by, reviewed_at, created_at)
    VALUES (s_vannak, 'Vannak Kim', '+855 12 345 678', '2001-04-12', 'Street 271, Toul Tom Poung, Phnom Penh', 'APPROVED',
            u_admin, v_now - interval '79 days', v_now - interval '80 days');
    INSERT INTO member_cards (card_number, student_id, holder_name, balance, status, created_at, updated_at)
    VALUES (pg_temp.demo_code('MC', 3, 4), s_vannak, 'Vannak Kim', 0, 'ACTIVE', v_now - interval '79 days', v_now - interval '79 days')
    ON CONFLICT (student_id) DO NOTHING
    RETURNING id INTO v_card;
    IF v_card IS NOT NULL THEN
        INSERT INTO member_card_top_up_requests (card_id, amount, note, status, reviewed_by, reviewed_at, created_at)
        VALUES (v_card, 200, 'Paid at the front desk', 'APPROVED', u_admin, v_now - interval '74 days', v_now - interval '75 days')
        RETURNING id INTO v_topup;
        INSERT INTO member_card_transactions (card_id, type, amount, balance_after, reference, idempotency_key, note, performed_by, created_at)
        VALUES (v_card, 'TOP_UP', 200, 200, 'TOPUP-' || v_topup, 'demo-topup-' || v_topup, 'Top-up request approved', u_admin, v_now - interval '74 days');

        v_order := pg_temp.demo_enroll(s_vannak, c_python, v_today - 60, NULL, NULL, 'member_card');
        INSERT INTO member_card_transactions (card_id, type, amount, balance_after, reference, idempotency_key, note, performed_by, created_at)
        VALUES (v_card, 'PURCHASE', 59.99, 140.01, v_order, 'demo-purchase-' || v_order, 'Python for Data Analysis', u_vannak,
                (v_today - 60)::timestamptz + interval '10 hours');
        UPDATE member_cards SET balance = 140.01, updated_at = v_now - interval '60 days' WHERE id = v_card;

        INSERT INTO member_card_top_up_requests (card_id, amount, note, status, created_at)
        VALUES (v_card, 50, 'Bank transfer from ABA, reference 88123', 'PENDING', v_now - interval '1 day');
    END IF;

    PERFORM pg_temp.demo_progress(s_vannak, c_python, 4, v_now - interval '58 days');
    INSERT INTO lesson_watch_positions (student_id, lesson_id, position_seconds, updated_at)
    VALUES (s_vannak, pg_temp.demo_lesson_id('python-for-data-analysis', 'Cleaning messy data'), 420, v_now - interval '3 days')
    ON CONFLICT DO NOTHING;
    INSERT INTO lesson_notes (student_id, lesson_id, body, position_seconds, created_at, updated_at)
    VALUES (s_vannak, l_py_pandas, 'read_csv(parse_dates=[...]) turns the date column into real dates straight away.', 305,
            v_now - interval '55 days', v_now - interval '55 days'),
           (s_vannak, l_py_pandas, 'Try this on the shop sales export from work.', NULL, v_now - interval '54 days', v_now - interval '54 days');

    INSERT INTO lesson_questions (lesson_id, author_id, author_name, body, resolved, created_at)
    VALUES (l_py_pandas, u_vannak, 'Vannak Kim', 'My CSV uses semicolons and read_csv puts everything in one column. What am I missing?',
            true, v_now - interval '56 days')
    RETURNING id INTO v_question;
    INSERT INTO lesson_answers (question_id, author_id, author_name, from_staff, body, created_at)
    VALUES (v_question, u_sokha, 'Sokha Chan', true, 'Pass sep='';'' to read_csv. Files exported from Excel in many regions use semicolons.',
            v_now - interval '55 days'),
           (v_question, u_vannak, 'Vannak Kim', false, 'That fixed it, thank you!', v_now - interval '55 days' + interval '1 hour');

    INSERT INTO cart_items (student_id, course_id, added_at)
    VALUES (s_vannak, c_spring, v_now - interval '4 days'), (s_vannak, c_ai, v_now - interval '2 days')
    ON CONFLICT DO NOTHING;
    INSERT INTO wishlist_items (student_id, course_id, created_at)
    VALUES (s_vannak, c_docker, v_now - interval '30 days'), (s_vannak, c_ux, v_now - interval '12 days')
    ON CONFLICT DO NOTHING;

    -- Sreymom: a graduate with three certificates, a finished learning path and reviews
    PERFORM pg_temp.demo_enroll(s_sreymom, c_git, v_today - 190);
    PERFORM pg_temp.demo_progress(s_sreymom, c_git, 100, v_now - interval '189 days');
    PERFORM pg_temp.demo_enroll(s_sreymom, c_ts, v_today - 170);
    PERFORM pg_temp.demo_progress(s_sreymom, c_ts, 100, v_now - interval '169 days');
    PERFORM pg_temp.demo_enroll(s_sreymom, c_nuxt, v_today - 150, NULL, 'NUXT25');
    PERFORM pg_temp.demo_progress(s_sreymom, c_nuxt, 100, v_now - interval '149 days');
    PERFORM pg_temp.demo_enroll(s_sreymom, c_ux, v_today - 30);
    PERFORM pg_temp.demo_progress(s_sreymom, c_ux, 100, v_now - interval '29 days');
    INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at, updated_at)
    VALUES (c_git, s_sreymom, 5, 'The perfect first course. Branching finally makes sense to me.', v_now - interval '180 days', v_now - interval '180 days'),
           (c_ts, s_sreymom, 5, 'Typed Pinia stores alone were worth it.', v_now - interval '160 days', v_now - interval '160 days'),
           (c_nuxt, s_sreymom, 4, 'Deep and practical. The rendering modes article could use a diagram.', v_now - interval '140 days', v_now - interval '140 days'),
           (c_ux, s_sreymom, 5, 'I ran my first usability test the week after finishing.', v_now - interval '20 days', v_now - interval '20 days')
    ON CONFLICT DO NOTHING;
    INSERT INTO lesson_questions (lesson_id, author_id, author_name, body, resolved, created_at)
    VALUES (pg_temp.demo_lesson_id('nuxt-3-mastery', 'Rendering modes explained'), u_sreymom, 'Sreymom Chea',
            'Can I mix SSR and static pages in one Nuxt app?', true, v_now - interval '146 days')
    RETURNING id INTO v_question;
    INSERT INTO lesson_answers (question_id, author_id, author_name, from_staff, body, created_at)
    VALUES (v_question, u_admin, 'Dara Sok', true, 'Yes. Use routeRules in nuxt.config to prerender some routes and render others on demand.',
            v_now - interval '145 days');

    -- Rithy: struggling. Out of quiz attempts, idle in another course, a failed and a cancelled payment
    PERFORM pg_temp.demo_enroll(s_rithy, c_python, v_today - 55, NULL, 'PYTHON15');
    PERFORM pg_temp.demo_progress(s_rithy, c_python, 5, v_now - interval '54 days');
    INSERT INTO quiz_attempts (student_id, lesson_id, correct_count, total_count, score_percent, passed, started_at, submitted_at)
    VALUES (s_rithy, l_py_quiz, 0, 3, 0, false, v_now - interval '40 days', v_now - interval '40 days' + interval '15 minutes'),
           (s_rithy, l_py_quiz, 1, 3, 33, false, v_now - interval '39 days', v_now - interval '39 days' + interval '11 minutes'),
           (s_rithy, l_py_quiz, 1, 3, 33, false, v_now - interval '38 days', v_now - interval '38 days' + interval '9 minutes');
    INSERT INTO quiz_attempt_grants (student_id, lesson_id, extra_attempts, granted_by, updated_at)
    VALUES (s_rithy, l_py_quiz, 1, u_sokha, v_now - interval '2 days')
    ON CONFLICT DO NOTHING;
    INSERT INTO lesson_questions (lesson_id, author_id, author_name, body, resolved, created_at)
    VALUES (pg_temp.demo_lesson_id('python-for-data-analysis', 'Cleaning messy data'), u_rithy, 'Rithy Pov',
            'When should I fill missing values instead of dropping the rows?', false, v_now - interval '6 hours');

    PERFORM pg_temp.demo_enroll(s_rithy, c_spring, v_today - 50);
    PERFORM pg_temp.demo_progress(s_rithy, c_spring, 1, v_now - interval '49 days');
    INSERT INTO learner_reminders (student_id, course_id, sent_at) VALUES (s_rithy, c_spring, v_now - interval '3 days')
    ON CONFLICT DO NOTHING;

    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider, created_at)
    VALUES (pg_temp.demo_code('ORD', 1, 16), s_rithy, c_docker, 119.99, 0, 119.99, 'USD', 'FAILED', 'fake', v_now - interval '20 days'),
           (pg_temp.demo_code('ORD', 1, 16), s_rithy, c_ai, 169.99, 0, 169.99, 'USD', 'CANCELLED', 'fake', v_now - interval '15 days');
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, created_at)
    VALUES (s_rithy, 'Rithy Pov', '+855 97 222 333', '1999-09-30', 'Sangkat Boeung Kak 1, Phnom Penh', 'PENDING', v_now - interval '5 hours');

    -- Nita: a refunded purchase, a cart checkout waiting for payment and a rejected card application
    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider,
                        provider_reference, created_at, paid_at, refunded_at)
    VALUES (pg_temp.demo_code('ORD', 1, 16), s_nita, c_docker, 119.99, 0, 119.99, 'USD', 'REFUNDED', 'fake', 'fake_refund_demo',
            v_now - interval '40 days', v_now - interval '40 days', v_now - interval '35 days');
    INSERT INTO order_groups (group_number, amount, currency, created_at)
    VALUES ('GRP-' || pg_temp.demo_code('ORD', 1, 16), 269.98, 'USD', v_now - interval '25 minutes')
    RETURNING id INTO v_group;
    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider,
                        checkout_url, group_id, created_at)
    VALUES (pg_temp.demo_code('ORD', 1, 16), s_nita, c_tailwind, 99.99, 0, 99.99, 'USD', 'PENDING', 'fake',
            'http://localhost:3000/checkout/fake', v_group, v_now - interval '25 minutes'),
           (pg_temp.demo_code('ORD', 1, 16), s_nita, c_ai, 169.99, 0, 169.99, 'USD', 'PENDING', 'fake',
            'http://localhost:3000/checkout/fake', v_group, v_now - interval '25 minutes');
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, review_note, reviewed_by, reviewed_at, created_at)
    VALUES (s_nita, 'Nita Ouk', '+66 81 234 5678', '2003-01-20', 'Sukhumvit Soi 11, Bangkok', 'REJECTED',
            'Please use the address on your national ID and apply again.', u_admin, v_now - interval '9 days', v_now - interval '10 days');
    PERFORM pg_temp.demo_enroll(s_nita, c_ux, v_today - 8);
    PERFORM pg_temp.demo_progress(s_nita, c_ux, 2, v_now - interval '7 days');

    -- Bopha: just signed up, browsing and saving courses
    INSERT INTO wishlist_items (student_id, course_id, created_at)
    VALUES (s_bopha, c_git, v_now - interval '20 hours'), (s_bopha, c_python, v_now - interval '19 hours')
    ON CONFLICT DO NOTHING;

    -- The Python cohort: every stage of the funnel for the instructor's analytics
    PERFORM pg_temp.demo_enroll(s_kosal, c_python, v_today - 85, NULL, NULL);
    PERFORM pg_temp.demo_progress(s_kosal, c_python, 6, v_now - interval '84 days');
    INSERT INTO assignment_submissions (assignment_id, student_id, comment, status, score, feedback, graded_by, graded_at, submitted_at)
    VALUES (v_assignment, s_kosal, 'June had the highest revenue: the mid-year promotion doubled orders from returning customers. Duplicates were 4% of rows.',
            'GRADED', 85, 'Clear reasoning and a tidy notebook. Next time, chart the monthly totals.', u_sokha,
            v_now - interval '70 days', v_now - interval '72 days')
    ON CONFLICT DO NOTHING;
    PERFORM pg_temp.demo_progress(s_kosal, c_python, 100, v_now - interval '84 days');
    INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at, updated_at)
    VALUES (c_python, s_kosal, 5, 'Step by step and never boring. The project feels like real work.', v_now - interval '60 days', v_now - interval '60 days')
    ON CONFLICT DO NOTHING;

    PERFORM pg_temp.demo_enroll(s_malis, c_python, v_today - 75, NULL, 'LAUNCH50');
    PERFORM pg_temp.demo_progress(s_malis, c_python, 6, v_now - interval '74 days');
    INSERT INTO assignment_submissions (assignment_id, student_id, comment, status, submitted_at)
    VALUES (v_assignment, s_malis, 'Revenue peaks in December. I removed 312 duplicate orders and filled missing regions from the store table.',
            'SUBMITTED', v_now - interval '1 day')
    ON CONFLICT DO NOTHING;
    INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at, updated_at)
    VALUES (c_python, s_malis, 4, 'Great course. The quiz timer made me nervous the first time.', v_now - interval '30 days', v_now - interval '30 days')
    ON CONFLICT DO NOTHING;

    PERFORM pg_temp.demo_enroll(s_piseth, c_python, v_today - 65);
    PERFORM pg_temp.demo_progress(s_piseth, c_python, 4, v_now - interval '64 days');
    PERFORM pg_temp.demo_enroll(s_piseth, c_ux, v_today - 60);
    PERFORM pg_temp.demo_progress(s_piseth, c_ux, 5, v_now - interval '59 days');
    INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at, updated_at)
    VALUES (c_ux, s_piseth, 4, 'Practical templates I reuse at work.', v_now - interval '50 days', v_now - interval '50 days')
    ON CONFLICT DO NOTHING;
    PERFORM pg_temp.demo_enroll(s_chantha, c_python, v_today - 60);
    PERFORM pg_temp.demo_progress(s_chantha, c_python, 2, v_now - interval '59 days');
    PERFORM pg_temp.demo_enroll(s_dalin, c_python, v_today - 45);
    PERFORM pg_temp.demo_progress(s_dalin, c_python, 1, v_now - interval '44 days');
    -- Just joined: the dripped bonus lesson is still locked for them
    PERFORM pg_temp.demo_enroll(s_sophea, c_python, v_today - 3);

    -- Kosal's card was suspended after an admin corrected a duplicate top-up
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, reviewed_by, reviewed_at, created_at)
    VALUES (s_kosal, 'Kosal Heng', '+855 16 888 999', '1998-12-05', 'Siem Reap town', 'APPROVED', u_admin, v_now - interval '50 days', v_now - interval '51 days');
    INSERT INTO member_cards (card_number, student_id, holder_name, balance, status, created_at, updated_at)
    VALUES (pg_temp.demo_code('MC', 3, 4), s_kosal, 'Kosal Heng', 0, 'SUSPENDED', v_now - interval '50 days', v_now - interval '15 days')
    ON CONFLICT (student_id) DO NOTHING
    RETURNING id INTO v_card;
    IF v_card IS NOT NULL THEN
        INSERT INTO member_card_transactions (card_id, type, amount, balance_after, reference, idempotency_key, note, performed_by, created_at)
        VALUES (v_card, 'TOP_UP', 20, 20, 'ADJ-' || v_card, 'demo-adjust-in-' || v_card, 'Cash top-up', u_admin, v_now - interval '49 days'),
               (v_card, 'DEDUCTION', 20, 0, 'ADJ-' || v_card || '-2', 'demo-adjust-out-' || v_card, 'Duplicate top-up reversed', u_admin, v_now - interval '15 days');
        INSERT INTO member_card_top_up_requests (card_id, amount, note, status, review_note, reviewed_by, reviewed_at, created_at)
        VALUES (v_card, 30, 'Transfer screenshot attached', 'REJECTED', 'The transfer has not reached our account.', u_admin,
                v_now - interval '16 days', v_now - interval '17 days');
    END IF;

    -- =====================================================================================================
    -- 6. Announcements, with the notification each enrolled learner got
    -- =====================================================================================================
    INSERT INTO course_announcements (course_id, author_id, author_name, title, body, created_at)
    VALUES (c_python, u_sokha, 'Sokha Chan', 'New practice dataset',
            'I added a second sales dataset to the project lesson so you can practise on fresh data.', v_now - interval '20 days'),
           (c_python, u_sokha, 'Sokha Chan', 'Live Q&A this Friday',
            'Join me on Friday at 7pm (Phnom Penh time) for a live Q&A about the project. Bring your notebooks!', v_now - interval '2 days');
    INSERT INTO notifications (user_id, type, title, message, link, created_at, read_at)
    SELECT st.user_id, 'ANNOUNCEMENT', 'Python for Data Analysis: ' || a.title, a.body,
           '/learn/python-for-data-analysis?announcement=' || a.id, a.created_at,
           CASE WHEN a.created_at < v_now - interval '7 days' THEN a.created_at + interval '1 day' END
    FROM course_announcements a
    JOIN student_courses sc ON sc.course_id = a.course_id
    JOIN students st ON st.id = sc.student_id AND st.user_id IS NOT NULL
    JOIN enrollments e ON e.student_id = sc.student_id AND e.course_id = sc.course_id AND e.enrolled_at <= a.created_at::date
    WHERE a.course_id = c_python;

    -- =====================================================================================================
    -- 7. Learning paths: published, published with an archived course hidden, and a draft
    -- =====================================================================================================
    INSERT INTO learning_paths (slug, title, description, thumbnail, status, created_at, updated_at)
    VALUES ('frontend-developer', 'Frontend Developer',
            'Version control, TypeScript and Nuxt, in the order teams use them. Finish all three for the path certificate.',
            'https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=800&h=450&fit=crop', 'PUBLISHED',
            v_now - interval '210 days', v_now - interval '30 days'),
           ('cloud-native-backend', 'Cloud-Native Backend Engineer',
            'Build Spring Boot services, ship them in containers and run them on Kubernetes.',
            NULL, 'PUBLISHED', v_now - interval '120 days', v_now - interval '40 days'),
           ('data-analyst-track', 'Data Analyst Track',
            'Python and SQL for everyday analysis. Coming soon.', NULL, 'DRAFT', v_now - interval '10 days', v_now - interval '10 days')
    ON CONFLICT (slug) DO NOTHING;
    INSERT INTO learning_path_courses (path_id, course_id, position)
    SELECT p.id, x.course_id, x.position
    FROM (VALUES ('frontend-developer', c_git, 0), ('frontend-developer', c_ts, 1), ('frontend-developer', c_nuxt, 2),
                 ('cloud-native-backend', c_spring, 0), ('cloud-native-backend', c_docker, 1), ('cloud-native-backend', c_cloud, 2),
                 ('data-analyst-track', c_python, 0), ('data-analyst-track', c_sql, 1)) AS x(slug, course_id, position)
    JOIN learning_paths p ON p.slug = x.slug
    WHERE x.course_id IS NOT NULL
    ON CONFLICT DO NOTHING;

    -- Sreymom finished every Frontend Developer course, so she holds its permanent certificate
    SELECT id INTO v_path FROM learning_paths WHERE slug = 'frontend-developer';
    INSERT INTO learning_path_certificates (certificate_number, path_id, student_id, learner_name, path_title, issued_at)
    VALUES (pg_temp.demo_code('CAH', 4, 4), v_path, s_sreymom, 'Sreymom Chea', 'Frontend Developer', v_now - interval '144 days')
    ON CONFLICT DO NOTHING;

    -- =====================================================================================================
    -- 8. Notifications for every role, read and unread
    -- =====================================================================================================
    -- Certificates, as CertificateService announces them
    INSERT INTO notifications (user_id, type, title, message, link, created_at, read_at)
    SELECT st.user_id, 'CERTIFICATE_ISSUED', 'Certificate earned', 'You completed ' || cc.course_title || '. Your certificate is ready.',
           '/certificates/' || cc.certificate_number, cc.issued_at, cc.issued_at + interval '3 hours'
    FROM course_certificates cc JOIN students st ON st.id = cc.student_id
    WHERE st.user_id IN (u_sreymom, u_kosal);
    INSERT INTO notifications (user_id, type, title, message, link, created_at, read_at)
    SELECT u_sreymom, 'CERTIFICATE_ISSUED', 'Learning path completed',
           'You completed every course in Frontend Developer. Your path certificate is ready.',
           '/certificates/' || certificate_number, issued_at, issued_at + interval '1 hour'
    FROM learning_path_certificates WHERE path_id = v_path AND student_id = s_sreymom;

    PERFORM pg_temp.demo_notify(u_vannak, 'ENROLLED', 'You''re enrolled', 'Python for Data Analysis is ready. Start with the first lesson.',
        '/learn/python-for-data-analysis', v_now - interval '60 days', true);
    PERFORM pg_temp.demo_notify(u_vannak, 'MEMBER_CARD', 'Top-up approved', 'Your member card was topped up with $200.00.',
        '/member-card', v_now - interval '74 days', true);
    PERFORM pg_temp.demo_notify(u_vannak, 'LESSON_QUESTION', 'New answer to your question', 'Sokha Chan answered your question in Loading data with pandas.',
        '/learn/python-for-data-analysis?lesson=' || l_py_pandas, v_now - interval '55 days', false);
    PERFORM pg_temp.demo_notify(u_vannak, 'NEW_LESSON', 'New lesson', 'Bonus: automating reports was added to Python for Data Analysis.',
        '/learn/python-for-data-analysis', v_now - interval '30 days', false);
    PERFORM pg_temp.demo_notify(u_rithy, 'PAYMENT_FAILED', 'Payment failed', 'Your payment for Docker & Kubernetes did not go through. You can try again from your orders.',
        '/orders', v_now - interval '20 days', true);
    PERFORM pg_temp.demo_notify(u_rithy, 'COURSE_REMINDER', 'Pick up where you left off', 'You''re 1 lesson into Spring Boot Microservices. Keep going!',
        '/learn/spring-boot-microservices', v_now - interval '3 days', false);
    PERFORM pg_temp.demo_notify(u_rithy, 'ASSIGNMENT', 'Extra quiz attempt', 'Sokha Chan gave you another attempt at the Data analysis quiz.',
        '/learn/python-for-data-analysis?lesson=' || l_py_quiz, v_now - interval '2 days', false);
    PERFORM pg_temp.demo_notify(u_nita, 'ORDER_REFUNDED', 'Order refunded', 'Your payment of $119.99 for Docker & Kubernetes was refunded.',
        '/orders', v_now - interval '35 days', true);
    PERFORM pg_temp.demo_notify(u_nita, 'MEMBER_CARD', 'Member card application not approved', 'Please use the address on your national ID and apply again.',
        '/member-card', v_now - interval '9 days', false);
    PERFORM pg_temp.demo_notify(u_kosal, 'ASSIGNMENT', 'Assignment graded', 'Your project scored 85%. Lesson complete!',
        '/learn/python-for-data-analysis?lesson=' || l_py_project, v_now - interval '70 days', true);

    PERFORM pg_temp.demo_notify(u_sokha, 'NEW_ENROLLMENT', 'New learner', 'Sophea Nhem enrolled in Python for Data Analysis.',
        '/instructor/courses/python-for-data-analysis', v_now - interval '3 days', false);
    PERFORM pg_temp.demo_notify(u_sokha, 'LESSON_QUESTION', 'New question', 'Rithy Pov asked a question in Cleaning messy data.',
        '/questions', v_now - interval '6 hours', false);
    PERFORM pg_temp.demo_notify(u_sokha, 'ASSIGNMENT', 'New submission to grade', 'Malis Tep submitted the Python project.',
        '/instructor/submissions', v_now - interval '1 day', false);
    PERFORM pg_temp.demo_notify(u_sokha, 'COURSE_REVIEW', 'Changes requested', 'An admin sent SQL for Analysts back with notes.',
        '/instructor/courses/sql-for-analysts', v_now - interval '20 days', true);
    PERFORM pg_temp.demo_notify(u_lina, 'COURSE_REVIEW', 'Course approved', 'UX Research Essentials is now live.',
        '/instructor/courses/ux-research-essentials', v_now - interval '199 days', true);

    PERFORM pg_temp.demo_notify(u_admin, 'COURSE_REVIEW', 'Course waiting for review', 'Sokha Chan sent Machine Learning Foundations for review.',
        '/admin/course-reviews', v_now - interval '12 days', false);
    PERFORM pg_temp.demo_notify(u_admin, 'MEMBER_CARD', 'New member card application', 'Rithy Pov applied for a member card.',
        '/admin/member-cards', v_now - interval '5 hours', false);
    PERFORM pg_temp.demo_notify(u_admin, 'MEMBER_CARD', 'Top-up waiting for approval', 'Vannak Kim asked to top up $50.00.',
        '/admin/member-cards', v_now - interval '1 day', false);
    PERFORM pg_temp.demo_notify(u_admin, 'COURSE_COMPLETED', 'Course completed', 'Kosal Heng completed Python for Data Analysis.',
        '/courses/python-for-data-analysis', v_now - interval '70 days', true);
    PERFORM pg_temp.demo_notify(u_admin, 'WEEKLY_REPORT', 'Your weekly report', 'New enrollments, revenue and completions for the last seven days.',
        '/analytics', v_now - interval '4 days', true);

    -- =====================================================================================================
    -- 9. The audit trail the admin and instructor actions above would have left
    -- =====================================================================================================
    INSERT INTO admin_audit_events (actor_id, actor_email, actor_role, action, http_method, path_pattern, resource, resource_id,
                                    path_variables, status, outcome, request_id, ip_address, created_at)
    VALUES
        (u_lina, 'lina.meas@demo.courseaurahub.io', 'INSTRUCTOR', 'InstructorController.create', 'POST', '/api/v1/instructor/courses',
         'courses', NULL, NULL, 201, 'SUCCESS', 'demo-0001', '10.0.0.21', v_now - interval '201 days'),
        (u_lina, 'lina.meas@demo.courseaurahub.io', 'INSTRUCTOR', 'InstructorController.submit', 'POST', '/api/v1/instructor/courses/{slug}/submit',
         'courses', 'ux-research-essentials', '{"slug":"ux-research-essentials"}', 200, 'SUCCESS', 'demo-0002', '10.0.0.21', v_now - interval '200 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'InstructorController.approve', 'POST', '/api/v1/admin/course-reviews/{slug}/approve',
         'course-reviews', 'ux-research-essentials', '{"slug":"ux-research-essentials"}', 200, 'SUCCESS', 'demo-0003', '10.0.0.5', v_now - interval '199 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'CouponController.create', 'POST', '/api/v1/admin/coupons',
         'coupons', NULL, NULL, 201, 'SUCCESS', 'demo-0004', '10.0.0.5', v_now - interval '100 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'CouponController.create', 'POST', '/api/v1/admin/coupons',
         'coupons', NULL, NULL, 409, 'FAILURE', 'demo-0005', '10.0.0.5', v_now - interval '100 days' + interval '1 minute'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'MemberCardController.approve', 'POST', '/api/v1/admin/member-cards/applications/{id}/approve',
         'member-cards', NULL, NULL, 200, 'SUCCESS', 'demo-0006', '10.0.0.5', v_now - interval '79 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'MemberCardTopUpController.approve', 'POST', '/api/v1/admin/member-cards/top-ups/{id}/approve',
         'member-cards', NULL, NULL, 200, 'SUCCESS', 'demo-0007', '10.0.0.5', v_now - interval '74 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'OrderController.refund', 'POST', '/api/v1/admin/orders/{orderNumber}/refund',
         'orders', NULL, NULL, 200, 'SUCCESS', 'demo-0008', '10.0.0.5', v_now - interval '35 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'InstructorController.reject', 'POST', '/api/v1/admin/course-reviews/{slug}/reject',
         'course-reviews', 'sql-for-analysts', '{"slug":"sql-for-analysts"}', 200, 'SUCCESS', 'demo-0009', '10.0.0.5', v_now - interval '20 days'),
        (u_admin, 'admin@demo.courseaurahub.io', 'ADMIN', 'MemberCardController.adjust', 'POST', '/api/v1/admin/member-cards/{id}/adjustments',
         'member-cards', NULL, NULL, 200, 'SUCCESS', 'demo-0010', '10.0.0.5', v_now - interval '15 days'),
        (u_sokha, 'sokha.chan@demo.courseaurahub.io', 'INSTRUCTOR', 'InstructorController.submit', 'POST', '/api/v1/instructor/courses/{slug}/submit',
         'courses', 'machine-learning-foundations', '{"slug":"machine-learning-foundations"}', 200, 'SUCCESS', 'demo-0011', '10.0.0.34', v_now - interval '12 days'),
        (u_sokha, 'sokha.chan@demo.courseaurahub.io', 'INSTRUCTOR', 'AnnouncementController.create', 'POST', '/api/v1/instructor/courses/{slug}/announcements',
         'courses', 'python-for-data-analysis', '{"slug":"python-for-data-analysis"}', 201, 'SUCCESS', 'demo-0012', '10.0.0.34', v_now - interval '2 days'),
        (u_sokha, 'sokha.chan@demo.courseaurahub.io', 'INSTRUCTOR', 'QuizController.grantAttempts', 'POST', '/api/v1/admin/lessons/{lessonId}/quiz/attempt-grants',
         'lessons', l_py_quiz::text, '{"lessonId":"' || l_py_quiz || '"}', 200, 'SUCCESS', 'demo-0013', '10.0.0.34', v_now - interval '2 days');

    -- =====================================================================================================
    -- 10. Derived values on the instructor courses: rating from reviews, completion rate, last update
    -- =====================================================================================================
    UPDATE courses c
    SET rating = coalesce((SELECT round(avg(r.rating), 1) FROM course_reviews r WHERE r.course_id = c.id), 0),
        updated_at = greatest(c.updated_at, v_today - 1)
    WHERE c.owner_id IN (u_sokha, u_lina);
    PERFORM pg_temp.demo_refresh_completion(c_python);
    PERFORM pg_temp.demo_refresh_completion(c_ux);

    RAISE NOTICE 'Lifecycle demo data seeded';
END
$seed$;
