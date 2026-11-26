-- Local demo seed, run once by config/DemoDataSeeder when app.demo-data.enabled=true and the database is local.
--
-- The seeder empties every table first (including the sample rows migrations V0013, V0017 and V0036 insert), then
-- runs this script in one transaction. It fills every table with data that follows the same rules as real writes:
-- counters, ledgers, certificates, earnings and quiz/exercise/assignment gates all agree with each other.
--
-- Every LOCAL account signs in with app.demo-data.password ({{DEMO_PASSWORD_HASH}} is replaced with its BCrypt hash).
-- Values written as 'seed:<raw>' in hash columns are hashed by the seeder afterwards, so seeded gift codes,
-- calendar feeds, invites and recovery codes really work. Helpers live in schema seed_tmp, dropped at the end.

SELECT setseed(0.2026);

DROP SCHEMA IF EXISTS seed_tmp CASCADE;
CREATE SCHEMA seed_tmp;

CREATE TABLE seed_tmp.settings AS
SELECT '{{DEMO_PASSWORD_HASH}}'::text AS password_hash,
       'https://www.youtube.com/watch?v=lYm8KEuWeNk&list=RDlYm8KEuWeNk&start_radio=1'::text AS video_url,
       '@demo.courseaurahub.io'::text AS domain;

-- Everyone the seed creates, so later sections can pick learners at random
CREATE TABLE seed_tmp.people (
    user_id uuid PRIMARY KEY,
    student_id int,
    email text NOT NULL,
    name text NOT NULL,
    kind text NOT NULL,              -- admin, instructor, persona, bulk
    joined timestamptz NOT NULL,
    card_id int,
    card_at timestamptz
);

-- ---------------------------------------------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------------------------------------------
CREATE FUNCTION seed_tmp.rint(lo int, hi int) RETURNS int LANGUAGE sql VOLATILE AS
$fn$ SELECT lo + floor(random() * (hi - lo + 1))::int $fn$;

CREATE FUNCTION seed_tmp.pick(p text[]) RETURNS text LANGUAGE sql VOLATILE AS
$fn$ SELECT p[1 + floor(random() * array_length(p, 1))::int] $fn$;

CREATE FUNCTION seed_tmp.hex(n int) RETURNS text LANGUAGE sql VOLATILE AS
$fn$ SELECT left(encode(sha256((random()::text || random()::text)::bytea), 'hex'), n) $fn$;

CREATE FUNCTION seed_tmp.b64(n int) RETURNS text LANGUAGE sql VOLATILE AS
$fn$ SELECT left(translate(encode(sha256((random()::text || 'a')::bytea) || sha256((random()::text || 'b')::bytea)
                                  || sha256((random()::text || 'c')::bytea), 'base64'), E'+/=\n', '-_'), n) $fn$;

-- Crockford base32 codes, the alphabet the app uses for order, card, certificate and gift numbers
CREATE FUNCTION seed_tmp.code(p_prefix text, p_groups int, p_length int) RETURNS text LANGUAGE plpgsql VOLATILE AS $fn$
DECLARE
    v_alphabet constant text := '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
    v_code text := p_prefix;
BEGIN
    FOR g IN 1..p_groups LOOP
        IF v_code <> '' THEN
            v_code := v_code || '-';
        END IF;
        FOR i IN 1..p_length LOOP
            v_code := v_code || substr(v_alphabet, 1 + floor(random() * 32)::int, 1);
        END LOOP;
    END LOOP;
    RETURN v_code;
END $fn$;

CREATE FUNCTION seed_tmp.uid(p_email text) RETURNS uuid LANGUAGE sql STABLE AS
$fn$ SELECT id FROM users WHERE email = lower(p_email) $fn$;

CREATE FUNCTION seed_tmp.sid(p_email text) RETURNS int LANGUAGE sql STABLE AS
$fn$ SELECT s.id FROM students s JOIN users u ON u.id = s.user_id WHERE u.email = lower(p_email) $fn$;

CREATE FUNCTION seed_tmp.cid(p_slug text) RETURNS int LANGUAGE sql STABLE AS
$fn$ SELECT id FROM courses WHERE slug = p_slug $fn$;

CREATE FUNCTION seed_tmp.admin_id() RETURNS uuid LANGUAGE sql STABLE AS
$fn$ SELECT id FROM users WHERE email = 'admin@demo.courseaurahub.io' $fn$;

CREATE FUNCTION seed_tmp.add_user(
    p_email text, p_first text, p_last text, p_role text, p_created timestamptz, p_kind text,
    p_bio text DEFAULT NULL, p_verified boolean DEFAULT true, p_enabled boolean DEFAULT true,
    p_provider text DEFAULT 'LOCAL'
) RETURNS uuid LANGUAGE plpgsql AS $fn$
DECLARE
    v_id uuid := gen_random_uuid();
BEGIN
    INSERT INTO users (id, first_name, last_name, email, password, role, provider, provider_id, enabled,
                       failed_login_attempts, email_verified, email_verified_at, mfa_enabled, token_version, bio,
                       image_url, created_at, updated_at, tokens_issued_at)
    VALUES (v_id, p_first, p_last, lower(p_email),
            CASE WHEN p_provider = 'LOCAL' THEN (SELECT password_hash FROM seed_tmp.settings) END,
            p_role, p_provider, CASE WHEN p_provider <> 'LOCAL' THEN seed_tmp.rint(100000000, 999999999)::text || seed_tmp.rint(100000, 999999) END,
            p_enabled, 0, p_verified, CASE WHEN p_verified THEN p_created + interval '4 minutes' END, false, 0, p_bio,
            'https://api.dicebear.com/9.x/avataaars/svg?seed=' || p_first || p_last, p_created,
            p_created + interval '5 minutes', CASE WHEN p_verified THEN p_created + interval '5 minutes' END);
    INSERT INTO seed_tmp.people (user_id, email, name, kind, joined)
    VALUES (v_id, lower(p_email), p_first || ' ' || p_last, p_kind, p_created);
    RETURN v_id;
END $fn$;

-- The learner record behind an account, linked the way LearnerAccountService links it
CREATE FUNCTION seed_tmp.add_student(p_user uuid, p_country text) RETURNS int LANGUAGE plpgsql AS $fn$
DECLARE
    v_id int;
BEGIN
    INSERT INTO students (name, email, avatar, total_spent, joined_at, last_active, status, country, user_id, reminders_enabled)
    SELECT u.first_name || ' ' || u.last_name, u.email, u.image_url, 0, u.created_at::date, u.created_at::date, 'ACTIVE',
           p_country, u.id, random() > 0.08
    FROM users u WHERE u.id = p_user
    RETURNING id INTO v_id;
    UPDATE seed_tmp.people SET student_id = v_id WHERE user_id = p_user;
    RETURN v_id;
END $fn$;

CREATE FUNCTION seed_tmp.card_tx(p_card int, p_type text, p_amount numeric, p_reference text, p_note text,
                                 p_by uuid, p_at timestamptz) RETURNS void LANGUAGE sql AS $fn$
    -- balance_after is recomputed in time order at the end of the seed
    INSERT INTO member_card_transactions (card_id, type, amount, balance_after, reference, idempotency_key, note, performed_by, created_at)
    VALUES (p_card, p_type, p_amount, 0, p_reference, 'seed-' || p_type || '-' || coalesce(p_reference, gen_random_uuid()::text),
            p_note, p_by, p_at)
$fn$;

CREATE FUNCTION seed_tmp.valid_coupon(p_course int, p_at timestamptz) RETURNS int LANGUAGE sql VOLATILE AS $fn$
    SELECT c.id FROM coupons c
    WHERE c.active AND (c.course_id IS NULL OR c.course_id = p_course)
      AND c.created_at <= p_at
      AND (c.starts_at IS NULL OR c.starts_at <= p_at) AND (c.expires_at IS NULL OR c.expires_at > p_at)
      AND (c.max_redemptions IS NULL
           OR (SELECT count(*) FROM orders o WHERE o.coupon_id = c.id AND o.status IN ('PAID', 'PENDING')) < c.max_redemptions)
    ORDER BY random() LIMIT 1
$fn$;

-- Enrolls a learner the way EnrollmentService does (counters are rebuilt from these rows at the end).
-- A paid enrollment gets its PAID order; p_paid = false is a free enrollment (subscription, gift, seat, free course).
CREATE FUNCTION seed_tmp.enroll(p_student int, p_course int, p_at timestamptz, p_provider text DEFAULT 'fake',
                                p_coupon int DEFAULT NULL, p_paid boolean DEFAULT true) RETURNS int LANGUAGE plpgsql AS $fn$
DECLARE
    v_list numeric;
    v_amount numeric;
    v_coupon coupons%ROWTYPE;
    v_number text;
    v_order int;
BEGIN
    IF p_student IS NULL OR p_course IS NULL
       OR EXISTS (SELECT 1 FROM student_courses WHERE student_id = p_student AND course_id = p_course) THEN
        RETURN NULL;
    END IF;
    SELECT CASE WHEN p_paid THEN price ELSE 0 END INTO v_list FROM courses WHERE id = p_course;
    v_amount := v_list;
    IF p_coupon IS NOT NULL AND v_list > 0 THEN
        SELECT * INTO v_coupon FROM coupons WHERE id = p_coupon;
        v_amount := greatest(0, round(CASE v_coupon.discount_type
            WHEN 'PERCENT' THEN v_list * (1 - v_coupon.discount_value / 100)
            ELSE v_list - v_coupon.discount_value END, 2));
    END IF;

    INSERT INTO student_courses (student_id, course_id, completed) VALUES (p_student, p_course, false);
    INSERT INTO enrollments (student_id, course_id, enrolled_at, amount) VALUES (p_student, p_course, p_at::date, v_amount);
    IF v_list > 0 THEN
        v_number := seed_tmp.code('ORD', 1, 16);
        INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status,
                            provider, provider_reference, checkout_url, coupon_id, created_at, paid_at)
        VALUES (v_number, p_student, p_course, v_list, v_list - v_amount, v_amount, 'USD', 'PAID',
                CASE WHEN v_amount > 0 THEN p_provider END,
                CASE WHEN v_amount > 0 AND p_provider = 'fake' THEN 'fake_' || lower(substr(v_number, 5)) END,
                CASE WHEN v_amount > 0 AND p_provider = 'fake' THEN 'http://localhost:3000/checkout/fake?order=' || v_number END,
                v_coupon.id, p_at - interval '4 minutes', p_at)
        RETURNING id INTO v_order;
    END IF;
    -- An enrolled course leaves the wishlist and the cart
    DELETE FROM wishlist_items WHERE student_id = p_student AND course_id = p_course;
    DELETE FROM cart_items WHERE student_id = p_student AND course_id = p_course;
    RETURN v_order;
END $fn$;

-- Completes up to p_count lessons in curriculum order from p_start, never in the future and never before a dripped
-- lesson opens. Quiz lessons get a passing attempt, CODE lessons a passing run and lessons with a required assignment
-- a passing grade, exactly as the app requires. Finishing the course completes it and issues its certificate.
CREATE FUNCTION seed_tmp.progress(p_student int, p_course int, p_count int, p_start timestamptz) RETURNS int
LANGUAGE plpgsql AS $fn$
DECLARE
    v_lesson record;
    v_at timestamptz := p_start;
    v_last timestamptz;
    v_done int := 0;
    v_total int;
    v_q int;
    v_draw int;
    v_need int;
    v_correct int;
    v_ids text;
    v_owner uuid;
    v_assignment assignments%ROWTYPE;
    v_exercise code_exercises%ROWTYPE;
    v_tests int;
    v_score int;
BEGIN
    IF p_student IS NULL OR p_course IS NULL OR p_count <= 0 THEN
        RETURN 0;
    END IF;
    SELECT coalesce(owner_id, seed_tmp.admin_id()) INTO v_owner FROM courses WHERE id = p_course;
    FOR v_lesson IN
        SELECT l.* FROM lessons l JOIN course_sections s ON s.id = l.section_id
        WHERE s.course_id = p_course ORDER BY s.position, s.id, l.position, l.id LIMIT p_count
    LOOP
        v_at := v_at + make_interval(hours => seed_tmp.rint(3, 52), mins => seed_tmp.rint(0, 59));
        IF v_lesson.release_after_days IS NOT NULL AND v_at < p_start + make_interval(days => v_lesson.release_after_days) THEN
            v_at := p_start + make_interval(days => v_lesson.release_after_days, hours => seed_tmp.rint(2, 30));
        END IF;
        EXIT WHEN v_at > now() - interval '40 minutes';

        IF v_lesson.type = 'QUIZ' THEN
            SELECT count(*) INTO v_q FROM quiz_questions WHERE lesson_id = v_lesson.id;
            IF v_q > 0 THEN
                v_draw := least(coalesce(v_lesson.quiz_questions_per_attempt, v_q), v_q);
                v_need := ceil(v_draw * v_lesson.quiz_pass_percent / 100.0);
                IF random() < 0.3 AND v_need > 0 THEN
                    v_correct := seed_tmp.rint(0, v_need - 1);
                    SELECT string_agg(id::text, ',') INTO v_ids FROM (SELECT id FROM quiz_questions WHERE lesson_id = v_lesson.id ORDER BY random() LIMIT v_draw) q;
                    INSERT INTO quiz_attempts (student_id, lesson_id, correct_count, total_count, score_percent, passed, started_at, submitted_at)
                    VALUES (p_student, v_lesson.id, v_correct, v_draw, round(100.0 * v_correct / v_draw), false,
                            v_at - interval '3 hours 12 minutes', v_at - interval '3 hours');
                    IF v_lesson.quiz_time_limit_minutes IS NOT NULL THEN
                        INSERT INTO quiz_sessions (student_id, lesson_id, question_ids, started_at, expires_at, submitted_at)
                        VALUES (p_student, v_lesson.id, v_ids, v_at - interval '3 hours 12 minutes',
                                v_at - interval '3 hours 12 minutes' + make_interval(mins => v_lesson.quiz_time_limit_minutes), v_at - interval '3 hours');
                    END IF;
                END IF;
                v_correct := seed_tmp.rint(v_need, v_draw);
                SELECT string_agg(id::text, ',') INTO v_ids FROM (SELECT id FROM quiz_questions WHERE lesson_id = v_lesson.id ORDER BY random() LIMIT v_draw) q;
                INSERT INTO quiz_attempts (student_id, lesson_id, correct_count, total_count, score_percent, passed, started_at, submitted_at)
                VALUES (p_student, v_lesson.id, v_correct, v_draw, round(100.0 * v_correct / v_draw), true,
                        v_at - interval '9 minutes', v_at - interval '1 minute');
                IF v_lesson.quiz_time_limit_minutes IS NOT NULL THEN
                    INSERT INTO quiz_sessions (student_id, lesson_id, question_ids, started_at, expires_at, submitted_at)
                    VALUES (p_student, v_lesson.id, v_ids, v_at - interval '9 minutes',
                            v_at - interval '9 minutes' + make_interval(mins => v_lesson.quiz_time_limit_minutes), v_at - interval '1 minute');
                END IF;
            END IF;
        ELSIF v_lesson.type = 'CODE' THEN
            SELECT * INTO v_exercise FROM code_exercises WHERE lesson_id = v_lesson.id;
            IF FOUND THEN
                v_tests := jsonb_array_length(v_exercise.examples) + jsonb_array_length(v_exercise.tests);
                IF random() < 0.45 THEN
                    INSERT INTO code_submissions (student_id, lesson_id, language, code, passed, passed_tests, total_tests, result, created_at)
                    VALUES (p_student, v_lesson.id, v_exercise.language, v_exercise.starter_code || E'\n# TODO\n', false,
                            seed_tmp.rint(0, v_tests - 1), v_tests,
                            jsonb_build_object('verdict', 'wrong_answer', 'message', 'Hidden test ' || seed_tmp.rint(1, 3) || ' failed'),
                            v_at - interval '25 minutes');
                END IF;
                INSERT INTO code_submissions (student_id, lesson_id, language, code, passed, passed_tests, total_tests, result, created_at)
                SELECT p_student, v_lesson.id, v_exercise.language, t.solution, true, v_tests, v_tests,
                       jsonb_build_object('verdict', 'accepted', 'message', ''), v_at - interval '2 minutes'
                FROM seed_tmp.code_templates t
                WHERE t.language = v_exercise.language AND t.title = (SELECT title FROM seed_tmp.code_templates x
                      WHERE x.language = v_exercise.language AND v_exercise.instructions LIKE x.instructions || '%' LIMIT 1);
            END IF;
        END IF;

        SELECT * INTO v_assignment FROM assignments WHERE lesson_id = v_lesson.id;
        IF FOUND THEN
            IF v_assignment.required AND random() < 0.15 THEN
                -- Handed in and waiting for the instructor, so the lesson can't be completed yet
                INSERT INTO assignment_submissions (assignment_id, student_id, file_url, file_name, comment, status, submitted_at)
                VALUES (v_assignment.id, p_student, NULL, NULL, seed_tmp.pick(ARRAY[
                    'Here is my project write-up. I focused on the edge cases we discussed in the last section.',
                    'Submitting my final project. The README explains how to run it.',
                    'My solution is attached. I was not sure about the last requirement, feedback welcome!']),
                    'SUBMITTED', v_at - interval '1 hour');
                EXIT;
            ELSIF v_assignment.required OR random() < 0.6 THEN
                v_score := CASE WHEN v_assignment.required THEN seed_tmp.rint(v_assignment.pass_score, 100) ELSE seed_tmp.rint(45, 100) END;
                INSERT INTO assignment_submissions (assignment_id, student_id, file_url, file_name, comment, status, score,
                                                    feedback, graded_by, graded_at, submitted_at)
                VALUES (v_assignment.id, p_student,
                        CASE WHEN random() < 0.4 THEN 'storage://private/assignments/' || gen_random_uuid() || '.pdf' END, NULL,
                        seed_tmp.pick(ARRAY[
                            'Final project attached. I added charts for each step and a short summary at the end.',
                            'My write-up and code are below. It took longer than expected but I learned a lot.',
                            'Project done! I tried the bonus challenge too.',
                            'Here is my submission with notes on the trade-offs I made.']),
                        'GRADED', v_score,
                        seed_tmp.pick(ARRAY['Clear reasoning and tidy work. Next time, add a short summary at the top.',
                            'Great job. Your structure is easy to follow and the edge cases are covered.',
                            'Good work overall. Watch the naming in the second part.',
                            'Excellent, one of the best submissions this month.',
                            'Solid effort. Please explain your assumptions a bit more next time.']),
                        v_owner, v_at - interval '30 minutes', v_at - make_interval(days => seed_tmp.rint(1, 3)));
                UPDATE assignment_submissions SET file_name = 'project-' || p_student || '.pdf'
                WHERE assignment_id = v_assignment.id AND student_id = p_student AND file_url IS NOT NULL;
            END IF;
        END IF;

        INSERT INTO lesson_progress (student_id, lesson_id, completed_at) VALUES (p_student, v_lesson.id, v_at);
        v_last := v_at;
        v_done := v_done + 1;
    END LOOP;

    SELECT count(*) INTO v_total FROM lessons l JOIN course_sections s ON s.id = l.section_id WHERE s.course_id = p_course;
    IF v_total > 0 AND (SELECT count(*) FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id
                        JOIN course_sections s ON s.id = l.section_id
                        WHERE lp.student_id = p_student AND s.course_id = p_course) >= v_total THEN
        UPDATE student_courses SET completed = true WHERE student_id = p_student AND course_id = p_course;
        INSERT INTO course_certificates (certificate_number, student_id, course_id, issued_at, learner_name, course_title, instructor)
        SELECT seed_tmp.code('CAH', 4, 4), p_student, c.id, v_last, st.name, c.title, c.instructor
        FROM courses c, students st WHERE c.id = p_course AND st.id = p_student
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN v_done;
END $fn$;

CREATE FUNCTION seed_tmp.review(p_student int, p_course int, p_at timestamptz, p_rating int DEFAULT NULL) RETURNS void
LANGUAGE plpgsql AS $fn$
DECLARE
    v_rating int := coalesce(p_rating, (ARRAY[5, 5, 5, 5, 5, 4, 4, 4, 4, 3, 3, 2, 1])[seed_tmp.rint(1, 13)]);
    v_at timestamptz := least(p_at, now() - interval '2 hours');
BEGIN
    INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at, updated_at)
    VALUES (p_course, p_student, v_rating, CASE v_rating
        WHEN 5 THEN seed_tmp.pick(ARRAY['Excellent course, clear and practical from start to finish.',
            'Best course I have taken on this topic. The projects feel like real work.',
            'The instructor explains hard ideas simply. Highly recommended.',
            'Worth every cent. I used what I learned at work the very next week.',
            'Great pacing, great examples and a really helpful Q&A section.',
            'I finally understand this topic. Thank you!'])
        WHEN 4 THEN seed_tmp.pick(ARRAY['Very good course. A few lessons could be shorter.',
            'Solid content and good exercises. I wanted one more project.',
            'Clear explanations. The audio in section two could be better.',
            'Practical and well organised. Would love a follow-up on advanced topics.'])
        WHEN 3 THEN seed_tmp.pick(ARRAY['Decent overview, but it moves too fast in the middle.',
            'Good material, though some videos feel a little dated.',
            'OK for beginners. I expected more depth.'])
        WHEN 2 THEN seed_tmp.pick(ARRAY['Too basic for the price.', 'Hard to follow without more examples.'])
        ELSE seed_tmp.pick(ARRAY['Not what the description promised.', 'The videos skip too many steps.']) END,
        v_at, v_at)
    ON CONFLICT DO NOTHING;
END $fn$;

-- ---------------------------------------------------------------------------------------------------------------
-- Reference data: quiz bank, coding exercise templates, the course catalog
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE seed_tmp.quiz_bank AS
SELECT q.track, q.ordinality::int AS position, q.value ->> 'p' AS prompt, q.value ->> 'e' AS explanation,
       ARRAY(SELECT jsonb_array_elements_text(q.value -> 'o')) AS options, (q.value ->> 'c')::int AS correct
FROM (SELECT t.key AS track, x.value, x.ordinality
      FROM jsonb_each($j${
  "web": [
    {"p": "Which HTML element is best for a page's main navigation?", "e": "<nav> tells browsers and screen readers that the links are navigation.", "o": ["<nav>", "<div>", "<section>", "<menu>"], "c": 0},
    {"p": "What does justify-content control in a flex container?", "e": "It aligns items along the main axis.", "o": ["Alignment along the main axis", "Font size", "Stacking order", "Border colors"], "c": 0},
    {"p": "Which keyword declares a block-scoped variable that can't be reassigned?", "e": "const bindings can't be reassigned.", "o": ["var", "let", "const", "static"], "c": 2},
    {"p": "What does a Promise represent?", "e": "A Promise is a value that may be available later.", "o": ["A value that may be available later", "A CSS rule", "A database row", "A loop"], "c": 0},
    {"p": "Which HTTP status code means Not Found?", "e": "404 means the server can't find the resource.", "o": ["200", "301", "404", "500"], "c": 2}],
  "backend": [
    {"p": "Which HTTP method replaces a resource and is idempotent?", "e": "PUT replaces the resource; repeating it has the same effect.", "o": ["POST", "PUT", "PATCH", "CONNECT"], "c": 1},
    {"p": "What does a database index mainly speed up?", "e": "Indexes speed up reads that filter, join or sort on the indexed columns.", "o": ["Reads that filter or sort", "Backups", "Schema migrations", "Network calls"], "c": 0},
    {"p": "What is a transaction for?", "e": "A transaction makes a group of changes succeed or fail together.", "o": ["Grouping changes so they succeed or fail together", "Compressing data", "Encrypting passwords", "Caching pages"], "c": 0},
    {"p": "Which status code fits a request that conflicts with the current state?", "e": "409 Conflict.", "o": ["400", "401", "409", "418"], "c": 2},
    {"p": "What does dependency injection give you?", "e": "Components receive what they need, so they are loosely coupled and easy to test.", "o": ["Loosely coupled, testable components", "Faster CPUs", "Smaller JAR files", "An automatic UI"], "c": 0}],
  "data": [
    {"p": "Which pandas function reads a CSV file?", "e": "pd.read_csv loads a CSV into a DataFrame.", "o": ["pd.read_csv", "pd.open", "pd.load_table", "csv.read"], "c": 0},
    {"p": "How do you drop rows with missing values?", "e": "dropna removes rows (or columns) that contain NaN.", "o": ["df.remove_na()", "df.dropna()", "df.clean()", "df.fillna(drop=True)"], "c": 1},
    {"p": "Which SQL clause filters groups after aggregation?", "e": "HAVING filters groups; WHERE filters rows.", "o": ["WHERE", "HAVING", "ORDER BY", "LIMIT"], "c": 1},
    {"p": "Which chart best shows a trend over time?", "e": "Line charts show change over a continuous axis such as time.", "o": ["Line chart", "Pie chart", "Scatter matrix", "Word cloud"], "c": 0},
    {"p": "What is the median of 3, 7, 9, 12, 20?", "e": "The middle value of the sorted list is 9.", "o": ["7", "9", "10.2", "12"], "c": 1}],
  "ai": [
    {"p": "What does a model learn during training?", "e": "Training adjusts parameters to minimise a loss function.", "o": ["Parameters that minimise a loss", "The user's password", "The GPU clock speed", "A fixed lookup table"], "c": 0},
    {"p": "What is overfitting?", "e": "An overfit model memorises training data and generalises poorly.", "o": ["Fitting training data so closely it fails on new data", "Using too little memory", "Training too fast", "A data cleaning step"], "c": 0},
    {"p": "What does retrieval-augmented generation add to an LLM?", "e": "RAG puts relevant documents into the prompt.", "o": ["Relevant documents in the prompt", "More GPUs", "A bigger vocabulary", "A lower temperature"], "c": 0},
    {"p": "Which metrics suit an imbalanced classification problem?", "e": "Accuracy hides poor minority-class performance; precision and recall don't.", "o": ["Accuracy only", "Precision and recall", "Mean squared error", "R-squared"], "c": 1},
    {"p": "What does tool calling let an agent do?", "e": "Tools let the model act through external APIs and data.", "o": ["Act through external APIs", "Skip the prompt", "Train itself", "Remove all hallucinations"], "c": 0}],
  "devops": [
    {"p": "What is a container image?", "e": "An image packages a filesystem and configuration to run an app.", "o": ["A packaged filesystem and config to run an app", "A virtual machine", "A Git branch", "A log file"], "c": 0},
    {"p": "Which Kubernetes object keeps a set of pods running?", "e": "A Deployment manages ReplicaSets that keep pods running.", "o": ["Deployment", "ConfigMap", "Namespace", "Secret"], "c": 0},
    {"p": "What does Infrastructure as Code mean?", "e": "Infrastructure is described in versioned files and applied by tools.", "o": ["Defining infrastructure in versioned files", "Writing code on servers", "Programming hardware", "Clicking through a console"], "c": 0},
    {"p": "What is a readiness probe for?", "e": "It tells Kubernetes when a pod can receive traffic.", "o": ["Telling Kubernetes when a pod can take traffic", "Encrypting traffic", "Scaling nodes", "Building images"], "c": 0},
    {"p": "What does CI usually run on every push?", "e": "Continuous integration builds and tests every change.", "o": ["Build and tests", "Only production deploys", "Database backups", "Nothing"], "c": 0}],
  "mobile": [
    {"p": "In Flutter, what is everything on screen built from?", "e": "Flutter UIs are trees of widgets.", "o": ["Widgets", "Activities", "Storyboards", "Fragments"], "c": 0},
    {"p": "Which language does modern Android development favour?", "e": "Google recommends Kotlin for Android.", "o": ["Kotlin", "Objective-C", "Perl", "PHP"], "c": 0},
    {"p": "What does SwiftUI's @State do?", "e": "@State stores view-local state and redraws the view when it changes.", "o": ["Stores view-local state that redraws the view", "Calls an API", "Creates a database", "Defines a color"], "c": 0},
    {"p": "Why test on a real device?", "e": "Performance and hardware behaviour differ from emulators.", "o": ["Performance and hardware differ from emulators", "It is required by law", "Emulators can't run apps", "It makes coding faster"], "c": 0},
    {"p": "Where should an app keep secrets like API keys?", "e": "Keep them on the server or in the platform's secure storage.", "o": ["Off the device or in secure storage", "Hard-coded in the source", "In the app name", "In the logs"], "c": 0}],
  "security": [
    {"p": "What does the principle of least privilege mean?", "e": "Grant only the access a task needs.", "o": ["Give only the access a task needs", "Give admins everything", "Share passwords", "Disable logging"], "c": 0},
    {"p": "Which attack injects SQL through user input?", "e": "SQL injection; prevent it with parameterised queries.", "o": ["SQL injection", "Phishing", "DDoS", "Tailgating"], "c": 0},
    {"p": "What does HTTPS protect?", "e": "TLS protects data in transit.", "o": ["Data in transit", "Data on disk", "The source code", "Passwords in the database"], "c": 0},
    {"p": "What is a CVE?", "e": "A public identifier for a known vulnerability.", "o": ["A public ID for a known vulnerability", "A firewall brand", "A type of malware", "An encryption key"], "c": 0},
    {"p": "Why hash passwords with a slow algorithm like bcrypt?", "e": "Slow hashes make brute-force guessing expensive.", "o": ["To make brute force expensive", "To make logins faster", "To compress them", "To share them safely"], "c": 0}],
  "design": [
    {"p": "How many users usually reveal most usability problems in one round?", "e": "Around five users find most issues in a single round.", "o": ["About five", "At least fifty", "Exactly one", "Every user you have"], "c": 0},
    {"p": "What should an interview question avoid?", "e": "Leading questions push people toward the answer you expect.", "o": ["Open wording", "Leading the participant", "Follow-up questions", "Silence"], "c": 1},
    {"p": "What is a design token?", "e": "A named design value such as a color, size or spacing.", "o": ["A named value like a color or spacing", "A login cookie", "A Figma plugin", "A font file"], "c": 0},
    {"p": "What contrast ratio does WCAG AA require for body text?", "e": "4.5:1 for normal text.", "o": ["3:1", "4.5:1", "7:1", "1.5:1"], "c": 1},
    {"p": "What is a prototype for?", "e": "Prototypes test ideas before you build them.", "o": ["Testing ideas before building them", "Replacing developers", "Storing assets", "Printing designs"], "c": 0}],
  "business": [
    {"p": "What is a minimum viable product?", "e": "The smallest product that tests your riskiest assumption.", "o": ["The smallest product that tests a key assumption", "A finished product", "A marketing plan", "A logo"], "c": 0},
    {"p": "What does CAC stand for?", "e": "Customer acquisition cost.", "o": ["Customer acquisition cost", "Cost after commission", "Cash and credit", "Customer account code"], "c": 0},
    {"p": "Which metric shows the share of visitors who complete a goal?", "e": "Conversion rate.", "o": ["Conversion rate", "Bounce rate", "Churn", "CPM"], "c": 0},
    {"p": "What is an emergency fund for?", "e": "Covering unexpected costs without going into debt.", "o": ["Unexpected expenses without debt", "Buying stocks", "Paying taxes early", "Holidays"], "c": 0},
    {"p": "What makes a presentation opening strong?", "e": "A hook tied to the audience's problem earns attention.", "o": ["A hook tied to the audience's problem", "Reading the agenda slide", "Apologising for nerves", "A long biography"], "c": 0}]
}$j$::jsonb) t, jsonb_array_elements(t.value) WITH ORDINALITY x) q;

CREATE TABLE seed_tmp.code_templates (language text, idx int, title text, instructions text, starter text, examples jsonb, tests jsonb, solution text);
INSERT INTO seed_tmp.code_templates VALUES
('python', 1, 'Sum of numbers', 'Read one line of space-separated integers and print their sum.',
 E'numbers = list(map(int, input().split()))\n# Print the sum of the numbers\n',
 '[{"input": "1 2 3", "expectedOutput": "6"}]', '[{"input": "10 20 30 40", "expectedOutput": "100"}, {"input": "-5 5", "expectedOutput": "0"}, {"input": "7", "expectedOutput": "7"}]',
 E'numbers = list(map(int, input().split()))\nprint(sum(numbers))\n'),
('python', 2, 'Count the vowels', 'Read a line of text and print how many vowels (a, e, i, o, u) it contains, ignoring case.',
 E'text = input()\n# Count the vowels\n',
 '[{"input": "Hello World", "expectedOutput": "3"}]', '[{"input": "AEIOU", "expectedOutput": "5"}, {"input": "rhythm", "expectedOutput": "0"}, {"input": "Data Analysis", "expectedOutput": "5"}]',
 E'text = input().lower()\nprint(sum(1 for ch in text if ch in "aeiou"))\n'),
('javascript', 1, 'Reverse a string', 'Read one line from standard input and print it reversed.',
 E'const input = require("fs").readFileSync(0, "utf8").trim();\n// Print the reversed string\n',
 '[{"input": "hello", "expectedOutput": "olleh"}]', '[{"input": "Nuxt", "expectedOutput": "txuN"}, {"input": "racecar", "expectedOutput": "racecar"}, {"input": "ab cd", "expectedOutput": "dc ba"}]',
 E'const input = require("fs").readFileSync(0, "utf8").trim();\nconsole.log([...input].reverse().join(""));\n'),
('javascript', 2, 'FizzBuzz', 'Read n and print the numbers 1..n, one per line, with Fizz for multiples of 3, Buzz for 5 and FizzBuzz for both.',
 E'const n = Number(require("fs").readFileSync(0, "utf8").trim());\n// Print FizzBuzz up to n\n',
 '[{"input": "5", "expectedOutput": "1\\n2\\nFizz\\n4\\nBuzz"}]', '[{"input": "3", "expectedOutput": "1\\n2\\nFizz"}, {"input": "15", "expectedOutput": "1\\n2\\nFizz\\n4\\nBuzz\\nFizz\\n7\\n8\\nFizz\\nBuzz\\n11\\nFizz\\n13\\n14\\nFizzBuzz"}]',
 E'const n = Number(require("fs").readFileSync(0, "utf8").trim());\nfor (let i = 1; i <= n; i++) console.log(i % 15 === 0 ? "FizzBuzz" : i % 3 === 0 ? "Fizz" : i % 5 === 0 ? "Buzz" : i);\n'),
('java', 1, 'Greet the learner', 'Read a name and print "Hello, <name>!".',
 E'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        String name = new Scanner(System.in).nextLine();\n        // Print the greeting\n    }\n}\n',
 '[{"input": "Dara", "expectedOutput": "Hello, Dara!"}]', '[{"input": "World", "expectedOutput": "Hello, World!"}, {"input": "Sokha Chan", "expectedOutput": "Hello, Sokha Chan!"}]',
 E'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        String name = new Scanner(System.in).nextLine();\n        System.out.println("Hello, " + name + "!");\n    }\n}\n'),
('java', 2, 'Largest number', 'Read space-separated integers on one line and print the largest.',
 E'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        String[] parts = new Scanner(System.in).nextLine().trim().split("\\\\s+");\n        // Print the largest number\n    }\n}\n',
 '[{"input": "3 9 2", "expectedOutput": "9"}]', '[{"input": "-4 -2 -9", "expectedOutput": "-2"}, {"input": "42", "expectedOutput": "42"}]',
 E'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        String[] parts = new Scanner(System.in).nextLine().trim().split("\\\\s+");\n        System.out.println(Arrays.stream(parts).mapToInt(Integer::parseInt).max().getAsInt());\n    }\n}\n');

-- The catalog. owner is the instructor's email name (null: managed by admins); popularity weights enrollments.
CREATE TABLE seed_tmp.catalog AS
SELECT x.*, NULL::int AS course_id
FROM jsonb_to_recordset($j$[
 {"ord": 1, "slug": "nuxt-3-vue-enterprise", "title": "Nuxt 3 & Vue 3 Enterprise Architecture", "category": "Web Development", "level": "ADVANCED", "price": 129.99, "original": 199.99, "owner": "visal.tan", "status": "PUBLISHED", "track": "web", "lang": "javascript", "popularity": 5, "days": 520, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1517694712202-14dd9538aa97", "tags": ["nuxt", "vue", "ssr", "architecture"],
  "description": "The complete guide to production Nuxt 3: SSR, SSG and hybrid rendering, the Nitro server engine, Pinia state architecture and enterprise deployment.",
  "sections": ["Getting started with Nuxt 3", "Rendering modes", "Data fetching and state", "Server routes with Nitro", "Deploying to production"]},
 {"ord": 2, "slug": "modern-javascript-from-zero", "title": "Modern JavaScript from Zero", "category": "Web Development", "level": "BEGINNER", "price": 0, "original": 0, "owner": "visal.tan", "status": "PUBLISHED", "track": "web", "lang": "javascript", "popularity": 6, "days": 600, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1461749280684-dccba630e2f6", "tags": ["javascript", "es2024", "beginner"],
  "description": "A free, friendly start to JavaScript: values, functions, arrays, objects, the DOM and async code, with small exercises after every section.",
  "sections": ["Values and variables", "Functions and scope", "Arrays and objects", "The DOM", "Async JavaScript"]},
 {"ord": 3, "slug": "typescript-for-vue-devs", "title": "TypeScript for Vue Developers", "category": "Web Development", "level": "INTERMEDIATE", "price": 69.99, "original": 99.99, "owner": "visal.tan", "status": "PUBLISHED", "track": "web", "lang": "javascript", "popularity": 4, "days": 480, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1516116216624-53e697fedbea", "tags": ["typescript", "vue", "pinia"],
  "description": "Type your Vue apps with confidence: props, emits, composables, Pinia stores and API clients, without fighting the compiler.",
  "sections": ["Types for Vue developers", "Typing components", "Generics in practice", "Typed Pinia stores", "Typed API clients"]},
 {"ord": 4, "slug": "tailwind-css-design-systems", "title": "Tailwind CSS Design Systems", "category": "Web Development", "level": "INTERMEDIATE", "price": 59.99, "original": 89.99, "owner": "lina.meas", "status": "PUBLISHED", "track": "design", "lang": null, "popularity": 3, "days": 410, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1559028012-481c04fa702d", "tags": ["tailwind", "css", "design system"],
  "description": "Turn a Figma style guide into a Tailwind design system: tokens, components, accessible states and dark mode that holds up.",
  "sections": ["Design tokens", "Layout and spacing", "Components and states", "Accessible patterns", "Dark mode and theming"]},
 {"ord": 5, "slug": "react-19-complete-guide", "title": "React 19: The Complete Guide", "category": "Web Development", "level": "INTERMEDIATE", "price": 89.99, "original": 149.99, "owner": "visal.tan", "status": "PUBLISHED", "track": "web", "lang": "javascript", "popularity": 5, "days": 300, "seq": false, "quizRules": true, "drip": false, "requiredAssignment": false, "thumb": "photo-1633356122544-f134324a6cee", "tags": ["react", "hooks", "server components"],
  "description": "Components, hooks, server components and actions in React 19, built around three real projects.",
  "sections": ["Thinking in components", "Hooks and state", "Forms and actions", "Server components", "Testing React apps"]},
 {"ord": 6, "slug": "spring-boot-microservices", "title": "Spring Boot 4 Microservices", "category": "Backend Development", "level": "ADVANCED", "price": 149.99, "original": 229.99, "owner": "dara.pich", "status": "PUBLISHED", "track": "backend", "lang": "java", "popularity": 4, "days": 500, "seq": false, "quizRules": true, "drip": false, "requiredAssignment": true, "thumb": "photo-1555066931-4365d14bab8c", "tags": ["spring boot", "java", "microservices"],
  "description": "Design, build and run Spring Boot microservices: REST APIs, persistence, messaging, resilience and observability.",
  "sections": ["Service boundaries", "REST APIs with Spring", "Persistence and transactions", "Messaging and events", "Resilience and observability"]},
 {"ord": 7, "slug": "java-21-fundamentals", "title": "Java 21 Fundamentals", "category": "Programming", "level": "BEGINNER", "price": 49.99, "original": 79.99, "owner": "dara.pich", "status": "PUBLISHED", "track": "backend", "lang": "java", "popularity": 4, "days": 560, "seq": true, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1515879218367-8466d910aaa4", "tags": ["java", "oop", "beginner"],
  "description": "Learn Java the modern way: records, pattern matching, collections, streams and virtual threads.",
  "sections": ["Java basics", "Classes and records", "Collections and streams", "Error handling", "Concurrency with virtual threads"]},
 {"ord": 8, "slug": "nodejs-rest-apis", "title": "Node.js REST APIs with Express", "category": "Backend Development", "level": "INTERMEDIATE", "price": 74.99, "original": 109.99, "owner": "dara.pich", "status": "PUBLISHED", "track": "backend", "lang": "javascript", "popularity": 3, "days": 380, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1627398242454-45a1465c2479", "tags": ["node", "express", "rest"],
  "description": "Build a production-ready REST API in Node.js: routing, validation, authentication, databases and tests.",
  "sections": ["Express essentials", "Validation and errors", "Authentication", "Working with databases", "Testing and deployment"]},
 {"ord": 9, "slug": "postgresql-performance", "title": "PostgreSQL Performance Tuning", "category": "Backend Development", "level": "ADVANCED", "price": 99.99, "original": 139.99, "owner": "dara.pich", "status": "PUBLISHED", "track": "backend", "lang": null, "popularity": 2, "days": 260, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1544383835-bda2bc66a55d", "tags": ["postgresql", "sql", "performance"],
  "description": "Read query plans, design indexes, tune configuration and fix the slow queries that hurt your app.",
  "sections": ["Reading EXPLAIN", "Index design", "Query patterns", "Locks and concurrency", "Configuration and monitoring"]},
 {"ord": 10, "slug": "python-for-data-analysis", "title": "Python for Data Analysis", "category": "Data Science", "level": "BEGINNER", "price": 59.99, "original": 89.99, "owner": "sokha.chan", "status": "PUBLISHED", "track": "data", "lang": "python", "popularity": 6, "days": 450, "seq": true, "quizRules": true, "drip": true, "requiredAssignment": true, "thumb": "photo-1551288049-bebda4e38f71", "tags": ["python", "pandas", "data analysis"],
  "description": "Go from spreadsheets to pandas. Clean real datasets, analyse them and present what you found, one step at a time.",
  "sections": ["Python basics", "Working with pandas", "Cleaning messy data", "Analysis and grouping", "Presenting results"]},
 {"ord": 11, "slug": "sql-for-analysts", "title": "SQL for Analysts", "category": "Data Science", "level": "BEGINNER", "price": 39.99, "original": 59.99, "owner": "sokha.chan", "status": "PUBLISHED", "track": "data", "lang": null, "popularity": 4, "days": 400, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1504868584819-f8e8b4b6d7e3", "tags": ["sql", "postgresql", "reporting"],
  "description": "Write the queries analysts write every day: joins, grouping, window functions and clean reporting views.",
  "sections": ["SELECT and filtering", "Joins without tears", "Grouping and aggregates", "Window functions", "Reporting views"]},
 {"ord": 12, "slug": "data-visualization-storytelling", "title": "Data Visualization & Storytelling", "category": "Data Science", "level": "INTERMEDIATE", "price": 54.99, "original": 79.99, "owner": "sokha.chan", "status": "PUBLISHED", "track": "data", "lang": "python", "popularity": 2, "days": 230, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1460925895917-afdab827c52f", "tags": ["visualization", "matplotlib", "storytelling"],
  "description": "Choose the right chart, design it clearly and tell a story with data that decision makers remember.",
  "sections": ["Choosing the right chart", "Charts with matplotlib", "Design for clarity", "Dashboards", "Telling the story"]},
 {"ord": 13, "slug": "machine-learning-foundations", "title": "Machine Learning Foundations", "category": "AI & Machine Learning", "level": "INTERMEDIATE", "price": 79.99, "original": 119.99, "owner": "sreyneang.prak", "status": "PUBLISHED", "track": "ai", "lang": "python", "popularity": 5, "days": 420, "seq": false, "quizRules": true, "drip": false, "requiredAssignment": true, "thumb": "photo-1555949963-aa79dcee981c", "tags": ["machine learning", "scikit-learn", "python"],
  "description": "Regression, classification and model evaluation with scikit-learn, explained with small real datasets.",
  "sections": ["What a model learns", "Regression", "Classification", "Evaluating models", "Feature engineering"]},
 {"ord": 14, "slug": "llm-agents-engineering", "title": "Building AI Agents with LLMs", "category": "AI & Machine Learning", "level": "ADVANCED", "price": 169.99, "original": 249.99, "owner": "sreyneang.prak", "status": "PUBLISHED", "track": "ai", "lang": "python", "popularity": 5, "days": 200, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1677442136019-21780ecad995", "tags": ["llm", "agents", "rag", "claude"],
  "description": "Prompting, tool calling, retrieval and evaluation: build AI agents that hold up in production.",
  "sections": ["How LLMs work", "Prompting for production", "Tool calling", "Retrieval-augmented generation", "Evaluating agents"]},
 {"ord": 15, "slug": "deep-learning-pytorch", "title": "Deep Learning with PyTorch", "category": "AI & Machine Learning", "level": "ADVANCED", "price": 139.99, "original": 199.99, "owner": "sreyneang.prak", "status": "PUBLISHED", "track": "ai", "lang": "python", "popularity": 3, "days": 330, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1620712943543-bcc4688e7485", "tags": ["deep learning", "pytorch", "neural networks"],
  "description": "Tensors, training loops, CNNs and transformers in PyTorch, with GPU tips from real projects.",
  "sections": ["Tensors and autograd", "Training loops", "Convolutional networks", "Transformers", "Shipping models"]},
 {"ord": 16, "slug": "prompt-engineering-everyone", "title": "Prompt Engineering for Everyone", "category": "AI & Machine Learning", "level": "BEGINNER", "price": 0, "original": 0, "owner": "sreyneang.prak", "status": "PUBLISHED", "track": "ai", "lang": null, "popularity": 6, "days": 150, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1676299081847-824916de030a", "tags": ["prompting", "ai", "productivity"],
  "description": "A free course on getting great results from AI assistants at work and school.",
  "sections": ["How AI assistants think", "Writing clear prompts", "Examples and structure", "Checking the output", "Everyday workflows"]},
 {"ord": 17, "slug": "docker-kubernetes-devops", "title": "Docker & Kubernetes for Developers", "category": "Cloud & DevOps", "level": "INTERMEDIATE", "price": 119.99, "original": 179.99, "owner": "maly.nguon", "status": "PUBLISHED", "track": "devops", "lang": null, "popularity": 5, "days": 470, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": true, "thumb": "photo-1605745341112-85968b19335b", "tags": ["docker", "kubernetes", "devops"],
  "description": "Containerise your apps, run them on Kubernetes and ship them with Helm and CI pipelines.",
  "sections": ["Containers", "Writing Dockerfiles", "Kubernetes basics", "Config, secrets and probes", "Shipping with Helm"]},
 {"ord": 18, "slug": "aws-cloud-practitioner", "title": "AWS Cloud Practitioner Bootcamp", "category": "Cloud & DevOps", "level": "BEGINNER", "price": 69.99, "original": 99.99, "owner": "maly.nguon", "status": "PUBLISHED", "track": "devops", "lang": null, "popularity": 4, "days": 360, "seq": false, "quizRules": true, "drip": false, "requiredAssignment": false, "thumb": "photo-1451187580459-43490279c0fa", "tags": ["aws", "cloud", "certification"],
  "description": "Everything you need for the AWS Cloud Practitioner exam: core services, pricing, security and practice questions.",
  "sections": ["Cloud concepts", "Core AWS services", "Security and IAM", "Pricing and billing", "Exam preparation"]},
 {"ord": 19, "slug": "terraform-infrastructure-as-code", "title": "Terraform: Infrastructure as Code", "category": "Cloud & DevOps", "level": "INTERMEDIATE", "price": 89.99, "original": 129.99, "owner": "maly.nguon", "status": "PUBLISHED", "track": "devops", "lang": null, "popularity": 2, "days": 210, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1558494949-ef010cbdcc31", "tags": ["terraform", "iac", "cloud"],
  "description": "Describe your infrastructure in code, review it like code and apply it safely across environments.",
  "sections": ["Terraform basics", "State and backends", "Modules", "Environments and workspaces", "Terraform in CI"]},
 {"ord": 20, "slug": "git-github-fundamentals", "title": "Git & GitHub Fundamentals", "category": "Programming", "level": "BEGINNER", "price": 0, "original": 0, "owner": null, "instructor": "CourseAura Team", "status": "PUBLISHED", "track": "web", "lang": null, "popularity": 6, "days": 640, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1556075798-4825dfaaf498", "tags": ["git", "github", "version control"],
  "description": "A free course on version control: commits, branches, merges, pull requests and team workflows.",
  "sections": ["Why version control", "Commits and history", "Branching and merging", "Working with GitHub", "Team workflows"]},
 {"ord": 21, "slug": "flutter-crossplatform-mobile", "title": "Flutter Cross-Platform Apps", "category": "Mobile Development", "level": "INTERMEDIATE", "price": 109.99, "original": 159.99, "owner": "ratanak.seng", "status": "PUBLISHED", "track": "mobile", "lang": null, "popularity": 4, "days": 390, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1512941937669-90a1b58e7e9c", "tags": ["flutter", "dart", "mobile"],
  "description": "One codebase for iOS and Android: Dart, widgets, state management, networking and publishing.",
  "sections": ["Dart in one hour", "Widgets and layout", "State management", "Networking and storage", "Publishing your app"]},
 {"ord": 22, "slug": "kotlin-android-development", "title": "Android Development with Kotlin", "category": "Mobile Development", "level": "INTERMEDIATE", "price": 94.99, "original": 139.99, "owner": "ratanak.seng", "status": "PUBLISHED", "track": "mobile", "lang": null, "popularity": 3, "days": 340, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1607252650355-f7fd0460ccdb", "tags": ["kotlin", "android", "jetpack compose"],
  "description": "Build modern Android apps with Kotlin and Jetpack Compose, from first screen to Play Store.",
  "sections": ["Kotlin essentials", "Jetpack Compose", "Navigation", "Data and Room", "Releasing to Play Store"]},
 {"ord": 23, "slug": "swiftui-ios-apps", "title": "SwiftUI iOS Apps", "category": "Mobile Development", "level": "BEGINNER", "price": 84.99, "original": 119.99, "owner": "ratanak.seng", "status": "PUBLISHED", "track": "mobile", "lang": null, "popularity": 2, "days": 180, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1621839673705-6617adf9e890", "tags": ["swift", "swiftui", "ios"],
  "description": "Learn Swift and SwiftUI by building three iPhone apps you can show in your portfolio.",
  "sections": ["Swift basics", "Views and modifiers", "State and data flow", "Lists and navigation", "App Store basics"]},
 {"ord": 24, "slug": "ethical-hacking-essentials", "title": "Ethical Hacking Essentials", "category": "Cybersecurity", "level": "INTERMEDIATE", "price": 129.99, "original": 189.99, "owner": "kimheng.long", "status": "PUBLISHED", "track": "security", "lang": null, "popularity": 4, "days": 430, "seq": true, "quizRules": true, "drip": false, "requiredAssignment": false, "thumb": "photo-1550751827-4bd374c3f58b", "tags": ["security", "pentesting", "owasp"],
  "description": "Think like an attacker: reconnaissance, web vulnerabilities, exploitation basics and responsible disclosure, in a legal lab.",
  "sections": ["Ethics and the lab", "Reconnaissance", "Web vulnerabilities", "Exploitation basics", "Reporting findings"]},
 {"ord": 25, "slug": "cloud-security-devsecops", "title": "Cloud Security & DevSecOps", "category": "Cybersecurity", "level": "ADVANCED", "price": 149.99, "original": 219.99, "owner": "kimheng.long", "status": "PUBLISHED", "track": "security", "lang": null, "popularity": 2, "days": 280, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1563986768609-322da13575f3", "tags": ["devsecops", "cloud security", "ci"],
  "description": "Secure your cloud and your pipeline: threat modelling, secrets, supply chain and runtime protection.",
  "sections": ["Threat modelling", "Identity and access", "Securing the pipeline", "Supply chain security", "Runtime protection"]},
 {"ord": 26, "slug": "ux-research-essentials", "title": "UX Research Essentials", "category": "UI/UX Design", "level": "BEGINNER", "price": 39.99, "original": 59.99, "owner": "lina.meas", "status": "PUBLISHED", "track": "design", "lang": null, "popularity": 4, "days": 440, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1586717791821-3f44a563fa4c", "tags": ["ux", "research", "usability"],
  "description": "Plan interviews, run usability tests and turn what you hear into product decisions your team trusts.",
  "sections": ["Research basics", "Interviews", "Usability testing", "Making sense of data", "Sharing insights"]},
 {"ord": 27, "slug": "figma-prototyping-masterclass", "title": "Figma Prototyping Masterclass", "category": "UI/UX Design", "level": "INTERMEDIATE", "price": 49.99, "original": 79.99, "owner": "lina.meas", "status": "PUBLISHED", "track": "design", "lang": null, "popularity": 3, "days": 240, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1609921212029-bb5a28e60960", "tags": ["figma", "prototyping", "ui"],
  "description": "Interactive prototypes with variables, conditional logic and smart animate.",
  "sections": ["Frames and auto layout", "Components and variants", "Interactions", "Variables and logic", "Handing off to developers"]},
 {"ord": 28, "slug": "digital-marketing-fundamentals", "title": "Digital Marketing Fundamentals", "category": "Marketing", "level": "BEGINNER", "price": 44.99, "original": 69.99, "owner": "sophal.keo", "status": "PUBLISHED", "track": "business", "lang": null, "popularity": 3, "days": 350, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1432888498266-38ffec3eaf0a", "tags": ["marketing", "social media", "ads"],
  "description": "Plan campaigns across search, social and email, and measure what actually works.",
  "sections": ["Marketing foundations", "Search and SEO", "Social media", "Email marketing", "Measuring results"]},
 {"ord": 29, "slug": "startup-product-management", "title": "Product Management for Startups", "category": "Business", "level": "INTERMEDIATE", "price": 79.99, "original": 119.99, "owner": "sophal.keo", "status": "PUBLISHED", "track": "business", "lang": null, "popularity": 3, "days": 300, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": true, "thumb": "photo-1531403009284-440f080d1e12", "tags": ["product", "startup", "strategy"],
  "description": "Find problems worth solving, shape an MVP, prioritise a roadmap and work well with engineers.",
  "sections": ["Discovering problems", "Shaping the MVP", "Roadmaps and priorities", "Working with engineers", "Metrics that matter"]},
 {"ord": 30, "slug": "personal-finance-young-professionals", "title": "Personal Finance for Young Professionals", "category": "Personal Development", "level": "BEGINNER", "price": 19.99, "original": 29.99, "owner": "sophal.keo", "status": "PUBLISHED", "track": "business", "lang": null, "popularity": 4, "days": 270, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1554224155-6726b3ff858f", "tags": ["finance", "budgeting", "investing"],
  "description": "Budget, save, borrow wisely and start investing, with templates you can use from day one.",
  "sections": ["Budgeting basics", "Saving and emergency funds", "Debt and credit", "Investing for beginners", "Planning ahead"]},
 {"ord": 31, "slug": "public-speaking-confidence", "title": "Public Speaking with Confidence", "category": "Personal Development", "level": "BEGINNER", "price": 24.99, "original": 39.99, "owner": null, "instructor": "Chenda Ly", "status": "PUBLISHED", "track": "business", "lang": null, "popularity": 2, "days": 190, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1475721027785-f74eccf877e2", "tags": ["speaking", "presentation", "soft skills"],
  "description": "Prepare, rehearse and deliver talks that people remember, even if you get nervous.",
  "sections": ["Finding your message", "Structuring a talk", "Voice and body language", "Handling nerves", "Q&A and feedback"]},
 {"ord": 32, "slug": "jquery-legacy-web", "title": "jQuery for Legacy Web Apps", "category": "Web Development", "level": "BEGINNER", "price": 19.99, "original": 39.99, "owner": "visal.tan", "status": "ARCHIVED", "track": "web", "lang": null, "popularity": 1, "days": 700, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1498050108023-c5249f4df085", "tags": ["jquery", "legacy"],
  "description": "Maintain older jQuery codebases safely. Archived: existing learners keep access.",
  "sections": ["Selectors and events", "AJAX the old way", "Migrating away"]},
 {"ord": 33, "slug": "rust-systems-programming", "title": "Rust Systems Programming", "category": "Programming", "level": "ADVANCED", "price": 119.99, "original": 169.99, "owner": "dara.pich", "status": "PENDING_REVIEW", "track": "backend", "lang": null, "popularity": 1, "days": 14, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1607799279861-4dd421887fb3", "tags": ["rust", "systems", "performance"],
  "description": "Ownership, borrowing, traits and async Rust for fast, safe systems software.",
  "sections": ["Ownership and borrowing", "Traits and generics", "Async Rust"]},
 {"ord": 34, "slug": "seo-content-strategy", "title": "SEO & Content Strategy", "category": "Marketing", "level": "INTERMEDIATE", "price": 54.99, "original": 79.99, "owner": "sophal.keo", "status": "PENDING_REVIEW", "track": "business", "lang": null, "popularity": 1, "days": 9, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1562577309-4932fdd64cd1", "tags": ["seo", "content"],
  "description": "Research keywords, plan content and grow organic traffic that converts.",
  "sections": ["Keyword research", "Content planning", "Technical SEO"]},
 {"ord": 35, "slug": "graphql-api-design", "title": "GraphQL API Design", "category": "Backend Development", "level": "INTERMEDIATE", "price": 69.99, "original": 69.99, "owner": "visal.tan", "status": "DRAFT", "track": "backend", "lang": null, "popularity": 1, "days": 35, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1555099962-4199c345e5dd", "tags": ["graphql", "api"],
  "reviewNote": "Great start. Please add a short quiz to the last section and a free preview lesson so learners can try it before buying.",
  "description": "Schemas, resolvers, pagination and performance for GraphQL APIs.",
  "sections": ["Schema design", "Resolvers and data loaders"]},
 {"ord": 36, "slug": "mlops-production", "title": "MLOps in Production", "category": "AI & Machine Learning", "level": "ADVANCED", "price": 129.99, "original": 129.99, "owner": "sreyneang.prak", "status": "DRAFT", "track": "ai", "lang": null, "popularity": 1, "days": 3, "seq": false, "quizRules": false, "drip": false, "requiredAssignment": false, "thumb": "photo-1518186285589-2f7649de83e0", "tags": ["mlops"],
  "description": "Versioning, deploying and monitoring machine learning models.",
  "sections": ["Model versioning"]}
]$j$::jsonb) AS x(ord int, slug text, title text, category text, level text, price numeric, original numeric, owner text,
                  instructor text, status text, track text, lang text, popularity int, days int, seq boolean,
                  "quizRules" boolean, drip boolean, "requiredAssignment" boolean, thumb text, tags text[],
                  "reviewNote" text, description text, sections text[]);

-- ===============================================================================================================
-- 1. Platform settings, categories, staff
-- ===============================================================================================================
INSERT INTO platform_settings (id, platform_name, platform_tagline, currency, default_language, notifications, profile)
VALUES (1, 'CourseAuraHub Academy', 'Learn modern tech, design and business from people who do it every day', 'USD', 'en',
        '{"newEnrollment": true, "courseCompletion": true, "weeklyReport": true, "marketingEmails": false}'::jsonb,
        '{"name": "Dara Sok", "role": "Platform Director", "email": "admin@demo.courseaurahub.io", "avatar": "https://api.dicebear.com/9.x/avataaars/svg?seed=DaraSok", "bio": "Runs the CourseAuraHub platform: catalog, payments and learner support.", "website": "https://courseaurahub.io", "twitter": "@courseaurahub"}'::jsonb);

INSERT INTO categories (name, created_at)
SELECT name, now() - make_interval(days => (700 - n * 10)::int)
FROM unnest(ARRAY['Web Development', 'Backend Development', 'Programming', 'Data Science', 'AI & Machine Learning',
                  'Cloud & DevOps', 'Mobile Development', 'Cybersecurity', 'UI/UX Design', 'Business', 'Marketing',
                  'Personal Development', 'Photography']) WITH ORDINALITY AS c(name, n);

DO $seed$
DECLARE
    v_user uuid;
    v_staff record;
BEGIN
    PERFORM seed_tmp.add_user('admin@demo.courseaurahub.io', 'Dara', 'Sok', 'ROLE_ADMIN', now() - interval '720 days', 'admin',
        'Runs the CourseAuraHub platform: catalog, payments and learner support.');
    PERFORM seed_tmp.add_user('support@demo.courseaurahub.io', 'Chenda', 'Ly', 'ROLE_ADMIN', now() - interval '500 days', 'admin',
        'Learner support lead. Also teaches Public Speaking with Confidence.');

    FOR v_staff IN SELECT * FROM (VALUES
        ('sokha.chan', 'Sokha', 'Chan', 'Data analyst turned teacher. Ten years of Python, SQL and dashboards for NGOs and banks in Phnom Penh.', 'Data analyst and Python teacher', 600),
        ('lina.meas', 'Lina', 'Meas', 'Product designer and UX researcher. I help teams talk to users before they build.', 'Product designer and UX researcher', 560),
        ('dara.pich', 'Dara', 'Pich', 'Backend engineer at a fintech in Phnom Penh. Java since version 6, Spring since Boot 1.', 'Backend engineer, Java and Spring', 620),
        ('visal.tan', 'Visal', 'Tan', 'Frontend lead who has shipped Vue and Nuxt apps for ten years. Loves fast, accessible web apps.', 'Frontend lead, Vue and Nuxt', 680),
        ('maly.nguon', 'Maly', 'Nguon', 'Platform engineer. Runs Kubernetes clusters for a living and teaches DevOps on weekends.', 'Platform engineer and DevOps coach', 520),
        ('ratanak.seng', 'Ratanak', 'Seng', 'Mobile developer with apps used by over a million people across Southeast Asia.', 'Mobile developer, Flutter and Kotlin', 450),
        ('sreyneang.prak', 'Sreyneang', 'Prak', 'Machine learning engineer working on language models for Khmer.', 'ML engineer, LLMs and agents', 470),
        ('kimheng.long', 'Kimheng', 'Long', 'Penetration tester and security trainer. OSCP, CISSP.', 'Security engineer and trainer', 480),
        ('sophal.keo', 'Sophal', 'Keo', 'Founder of two startups. Now helps young teams find product-market fit.', 'Founder and product coach', 400)
    ) AS s(handle, first, last, bio, headline, days)
    LOOP
        v_user := seed_tmp.add_user(v_staff.handle || '@demo.courseaurahub.io', v_staff.first, v_staff.last, 'ROLE_INSTRUCTOR',
                                    now() - make_interval(days => v_staff.days), 'instructor', v_staff.bio);
        UPDATE users SET website = 'https://' || replace(v_staff.handle, '.', '') || '.example.com',
                         twitter = '@' || replace(v_staff.handle, '.', '_') WHERE id = v_user;
        INSERT INTO instructor_profiles (user_id, handle, headline, linkedin_url, github_url, youtube_url, created_at, updated_at)
        VALUES (v_user, replace(v_staff.handle, '.', '-'), v_staff.headline,
                'https://www.linkedin.com/in/' || replace(v_staff.handle, '.', '-') || '-demo',
                CASE WHEN v_staff.handle IN ('sokha.chan', 'dara.pich', 'visal.tan', 'maly.nguon', 'sreyneang.prak', 'kimheng.long')
                     THEN 'https://github.com/' || replace(v_staff.handle, '.', '-') || '-demo' END,
                CASE WHEN v_staff.handle IN ('sokha.chan', 'visal.tan', 'ratanak.seng') THEN 'https://www.youtube.com/@' || replace(v_staff.handle, '.', '') END,
                now() - make_interval(days => v_staff.days - 5), now() - make_interval(days => seed_tmp.rint(1, 60)));
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 2. Courses, curriculum, quizzes, coding exercises, assignments, study sources
-- ===============================================================================================================
DO $seed$
DECLARE
    v_c record;
    v_course int;
    v_section int;
    v_lesson int;
    v_pos int;
    v_sec text;
    v_last int;
    v_video text := (SELECT video_url FROM seed_tmp.settings);
    v_video_tpl text[] := ARRAY['%s: the big picture', '%s step by step', 'Hands-on: %s', '%s in a real project',
                                'Common mistakes in %s', 'Going deeper: %s'];
    v_article_tpl text[] := ARRAY['%s cheat sheet', 'Reading: %s best practices', '%s reference notes'];
    v_minutes int;
    v_tpl seed_tmp.code_templates%ROWTYPE;
    v_question int;
    v_q record;
BEGIN
    FOR v_c IN SELECT * FROM seed_tmp.catalog ORDER BY ord LOOP
        INSERT INTO courses (slug, title, description, instructor, price, original_price, status, category, level, thumbnail,
                             rating, total_students, total_lessons, total_hours, revenue, completion_rate, created_at,
                             updated_at, owner_id, review_note, sequential_lessons)
        VALUES (v_c.slug, v_c.title, v_c.description,
                coalesce(v_c.instructor, (SELECT first_name || ' ' || last_name FROM users WHERE email = v_c.owner || '@demo.courseaurahub.io')),
                v_c.price, v_c.original, v_c.status, v_c.category, v_c.level,
                'https://images.unsplash.com/' || v_c.thumb || '?w=800&h=450&fit=crop',
                0, 0, 0, 0, 0, 0, (now() - make_interval(days => v_c.days))::date,
                (now() - make_interval(days => least(v_c.days, seed_tmp.rint(1, 40))))::date,
                seed_tmp.uid(v_c.owner || '@demo.courseaurahub.io'), v_c."reviewNote", v_c.seq)
        RETURNING id INTO v_course;
        UPDATE seed_tmp.catalog SET course_id = v_course WHERE slug = v_c.slug;

        INSERT INTO course_tags (course_id, tag_order, tag)
        SELECT v_course, t.n - 1, t.tag FROM unnest(v_c.tags) WITH ORDINALITY AS t(tag, n);
        INSERT INTO course_monthly_metrics (course_id, month, enrollments, revenue)
        SELECT v_course, m, 0, 0 FROM generate_series(1, 12) AS m;

        v_last := array_length(v_c.sections, 1);
        FOR s IN 1..v_last LOOP
            v_sec := v_c.sections[s];
            INSERT INTO course_sections (course_id, title, position) VALUES (v_course, v_sec, s - 1) RETURNING id INTO v_section;
            v_pos := 0;
            -- Two videos and a reading per section
            FOR k IN 0..1 LOOP
                v_minutes := seed_tmp.rint(7, 34);
                INSERT INTO lessons (section_id, title, type, duration_minutes, duration_seconds, content_url, is_preview, position)
                VALUES (v_section, format(v_video_tpl[1 + ((s - 1) * 2 + k) % 6], v_sec), 'VIDEO', v_minutes,
                        v_minutes * 60 + seed_tmp.rint(0, 59), v_video, s = 1 AND k = 0, v_pos);
                v_pos := v_pos + 1;
            END LOOP;
            INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, is_preview, position)
            VALUES (v_section, format(v_article_tpl[1 + (s - 1) % 3], v_sec), 'ARTICLE', seed_tmp.rint(6, 16),
                    'https://cdn.courseaurahub.io/' || v_c.slug || '/notes-' || s, s = 1 AND v_c.ord % 3 = 0, v_pos);
            v_pos := v_pos + 1;

            -- A quiz every second section
            IF s % 2 = 0 THEN
                INSERT INTO lessons (section_id, title, type, duration_minutes, position, quiz_pass_percent, quiz_max_attempts,
                                     quiz_retry_cooldown_minutes, quiz_time_limit_minutes, quiz_questions_per_attempt, quiz_shuffle)
                VALUES (v_section, 'Quiz: ' || v_sec, 'QUIZ', seed_tmp.rint(5, 12), v_pos,
                        CASE WHEN v_c."quizRules" THEN 60 ELSE 70 END,
                        CASE WHEN v_c."quizRules" THEN 3 END, CASE WHEN v_c."quizRules" THEN 30 END,
                        CASE WHEN v_c."quizRules" THEN 15 END, CASE WHEN v_c."quizRules" THEN 4 END, v_c."quizRules")
                RETURNING id INTO v_lesson;
                v_pos := v_pos + 1;
                FOR v_q IN SELECT * FROM seed_tmp.quiz_bank WHERE track = v_c.track ORDER BY position LOOP
                    INSERT INTO quiz_questions (lesson_id, position, prompt, explanation)
                    VALUES (v_lesson, v_q.position - 1, v_q.prompt, v_q.explanation) RETURNING id INTO v_question;
                    INSERT INTO quiz_options (question_id, position, text, correct)
                    SELECT v_question, o.n - 1, o.text, o.n - 1 = v_q.correct FROM unnest(v_q.options) WITH ORDINALITY AS o(text, n);
                END LOOP;
            END IF;

            -- Coding exercises in programming courses
            IF v_c.lang IS NOT NULL AND s IN (2, 4) THEN
                SELECT * INTO v_tpl FROM seed_tmp.code_templates WHERE language = v_c.lang AND idx = s / 2;
                INSERT INTO lessons (section_id, title, type, duration_minutes, position)
                VALUES (v_section, 'Coding exercise: ' || v_tpl.title, 'CODE', seed_tmp.rint(15, 25), v_pos)
                RETURNING id INTO v_lesson;
                v_pos := v_pos + 1;
                INSERT INTO code_exercises (lesson_id, language, instructions, starter_code, examples, tests, time_limit_seconds, memory_limit_mb, updated_at)
                VALUES (v_lesson, v_c.lang, v_tpl.instructions || ' This exercise practises ' || lower(v_sec) || '.', v_tpl.starter,
                        v_tpl.examples, v_tpl.tests, CASE v_c.lang WHEN 'java' THEN 5.0 ELSE 2.0 END,
                        CASE v_c.lang WHEN 'java' THEN 256 ELSE 128 END, now() - make_interval(days => seed_tmp.rint(10, 200)));
            END IF;

            -- The final project, with its assignment
            IF s = v_last AND v_last >= 3 THEN
                INSERT INTO lessons (section_id, title, type, duration_minutes, content_url, position, release_after_days)
                VALUES (v_section, 'Final project: ' || v_c.title, 'ARTICLE', seed_tmp.rint(40, 90),
                        'https://cdn.courseaurahub.io/' || v_c.slug || '/final-project', v_pos,
                        CASE WHEN v_c.drip THEN 14 END)
                RETURNING id INTO v_lesson;
                v_pos := v_pos + 1;
                INSERT INTO assignments (lesson_id, instructions, pass_score, required, created_at, updated_at)
                VALUES (v_lesson,
                        'Build a small project that uses what you learned in ' || v_c.title || '. Describe your approach, '
                        || 'what you would improve with more time, and attach your work as a PDF or paste a link to your repository.',
                        CASE WHEN v_c."requiredAssignment" THEN 60 ELSE 50 END, v_c."requiredAssignment",
                        now() - make_interval(days => v_c.days - 3), now() - make_interval(days => seed_tmp.rint(5, 90)));
            END IF;
        END LOOP;
    END LOOP;

    -- Study text the assistant answers from, for the first lessons of the popular published courses
    INSERT INTO lesson_ai_sources (lesson_id, body, updated_by, updated_at)
    SELECT l.id,
           'Study notes for "' || l.title || '" in ' || c.title || E'.\n\nKey ideas:\n'
           || '1. Start from the problem the technique solves and why it matters in real projects.' || E'\n'
           || '2. Walk through the core concepts in small steps with one worked example.' || E'\n'
           || '3. Point out the common mistakes learners make and how to spot them early.' || E'\n'
           || '4. Finish with a short checklist to apply the lesson at work.' || E'\n\n'
           || 'Vocabulary: ' || array_to_string(c.tags, ', ') || '. Level: ' || lower(c.level) || '.',
           c.owner_id, now() - make_interval(days => seed_tmp.rint(5, 120))
    FROM (SELECT l.*, row_number() OVER (PARTITION BY s.course_id ORDER BY s.position, l.position) AS rn, s.course_id
          FROM lessons l JOIN course_sections s ON s.id = l.section_id WHERE l.type IN ('VIDEO', 'ARTICLE')) l
    JOIN (SELECT co.*, ARRAY(SELECT tag FROM course_tags t WHERE t.course_id = co.id ORDER BY tag_order) AS tags
          FROM courses co WHERE co.status = 'PUBLISHED') c ON c.id = l.course_id
    WHERE l.rn <= 6;

    INSERT INTO lesson_ai_summaries (lesson_id, source_hash, summary, created_at)
    SELECT a.lesson_id, encode(sha256(a.body::bytea), 'hex'),
           E'- The lesson explains the problem first, then the technique that solves it.\n'
           || E'- A worked example shows each step.\n- Watch for the common mistakes listed at the end.\n'
           || '- Use the checklist to apply it at work.', a.updated_at + interval '1 day'
    FROM lesson_ai_sources a WHERE random() < 0.5;
END $seed$;

-- ===============================================================================================================
-- 3. Promotions, revenue shares, prerequisites
-- ===============================================================================================================
INSERT INTO coupons (code, discount_type, discount_value, course_id, max_redemptions, starts_at, expires_at, active, created_at) VALUES
    ('WELCOME10', 'PERCENT', 10, NULL, NULL, NULL, NULL, true, now() - interval '700 days'),
    ('STUDENT20', 'PERCENT', 20, NULL, NULL, NULL, NULL, true, now() - interval '400 days'),
    ('FLAT5', 'FIXED', 5, NULL, NULL, NULL, NULL, true, now() - interval '380 days'),
    ('NUXT25', 'PERCENT', 25, seed_tmp.cid('nuxt-3-vue-enterprise'), NULL, NULL, NULL, true, now() - interval '500 days'),
    ('PYTHON15', 'PERCENT', 15, seed_tmp.cid('python-for-data-analysis'), NULL, NULL, NULL, true, now() - interval '440 days'),
    ('LAUNCH50', 'PERCENT', 50, seed_tmp.cid('llm-agents-engineering'), 20, NULL, NULL, true, now() - interval '200 days'),
    ('SUMMER20', 'PERCENT', 20, NULL, NULL, now() - interval '160 days', now() - interval '100 days', true, now() - interval '170 days'),
    ('BLACKFRIDAY40', 'PERCENT', 40, NULL, 300, now() - interval '2 days', now() + interval '8 days', true, now() - interval '10 days'),
    ('NEWYEAR30', 'PERCENT', 30, NULL, 500, now() + interval '40 days', now() + interval '55 days', true, now() - interval '1 day'),
    ('PAUSED10', 'FIXED', 10, NULL, NULL, NULL, NULL, false, now() - interval '60 days'),
    ('DEVOPS30', 'FIXED', 30, seed_tmp.cid('docker-kubernetes-devops'), 50, now() - interval '90 days', now() + interval '30 days', true, now() - interval '91 days');

INSERT INTO course_discounts (course_id, discount_type, discount_value, starts_at, ends_at, active, created_at) VALUES
    (seed_tmp.cid('ux-research-essentials'), 'PERCENT', 30, now() - interval '5 days', now() + interval '20 days', true, now() - interval '6 days'),
    (seed_tmp.cid('react-19-complete-guide'), 'PERCENT', 25, now() - interval '1 day', now() + interval '6 days', true, now() - interval '2 days'),
    (seed_tmp.cid('spring-boot-microservices'), 'FIXED', 40, now() - interval '3 days', now() + interval '11 days', true, now() - interval '4 days'),
    (seed_tmp.cid('personal-finance-young-professionals'), 'FIXED', 5, now() - interval '12 days', NULL, true, now() - interval '12 days'),
    (seed_tmp.cid('llm-agents-engineering'), 'PERCENT', 20, now() + interval '7 days', now() + interval '14 days', true, now() - interval '1 day'),
    (seed_tmp.cid('docker-kubernetes-devops'), 'FIXED', 20, now() - interval '40 days', now() - interval '10 days', true, now() - interval '41 days'),
    (seed_tmp.cid('tailwind-css-design-systems'), 'PERCENT', 15, now() - interval '3 days', NULL, false, now() - interval '4 days'),
    (seed_tmp.cid('flutter-crossplatform-mobile'), 'PERCENT', 35, now() - interval '200 days', now() - interval '180 days', true, now() - interval '201 days');

INSERT INTO course_revenue_shares (course_id, instructor_percent, updated_at) VALUES
    (seed_tmp.cid('python-for-data-analysis'), 80, now() - interval '200 days'),
    (seed_tmp.cid('llm-agents-engineering'), 75, now() - interval '150 days'),
    (seed_tmp.cid('nuxt-3-vue-enterprise'), 72.5, now() - interval '300 days'),
    (seed_tmp.cid('personal-finance-young-professionals'), 60, now() - interval '100 days'),
    (seed_tmp.cid('ethical-hacking-essentials'), 75, now() - interval '250 days');

INSERT INTO course_prerequisites (course_id, required_course_id, hard, position) VALUES
    (seed_tmp.cid('deep-learning-pytorch'), seed_tmp.cid('machine-learning-foundations'), true, 0),
    (seed_tmp.cid('llm-agents-engineering'), seed_tmp.cid('machine-learning-foundations'), false, 0),
    (seed_tmp.cid('llm-agents-engineering'), seed_tmp.cid('python-for-data-analysis'), false, 1),
    (seed_tmp.cid('machine-learning-foundations'), seed_tmp.cid('python-for-data-analysis'), false, 0),
    (seed_tmp.cid('spring-boot-microservices'), seed_tmp.cid('java-21-fundamentals'), false, 0),
    (seed_tmp.cid('nuxt-3-vue-enterprise'), seed_tmp.cid('typescript-for-vue-devs'), false, 0),
    (seed_tmp.cid('terraform-infrastructure-as-code'), seed_tmp.cid('aws-cloud-practitioner'), false, 0),
    (seed_tmp.cid('cloud-security-devsecops'), seed_tmp.cid('docker-kubernetes-devops'), false, 0);

-- ===============================================================================================================
-- 4. Personas: hand-made learner journeys, plus the special account states
-- ===============================================================================================================
DO $seed$
DECLARE
    v_admin uuid := seed_tmp.admin_id();
    u uuid;
    s int;
    v_card int;
    v_topup int;
    v_order int;
    v_number text;
    v_question int;
    v_group int;
BEGIN
    -- Vannak: active learner with a member card, three courses in progress and one finished
    u := seed_tmp.add_user('vannak.kim@demo.courseaurahub.io', 'Vannak', 'Kim', 'ROLE_USER', now() - interval '140 days', 'persona',
        'Junior web developer learning the full stack.');
    s := seed_tmp.add_student(u, 'Cambodia');
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, reviewed_by, reviewed_at, created_at)
    VALUES (s, 'Vannak Kim', '+855 12 345 678', '2001-04-12', 'Street 271, Toul Tom Poung, Phnom Penh', 'APPROVED', v_admin,
            now() - interval '134 days', now() - interval '135 days');
    INSERT INTO member_cards (card_number, student_id, holder_name, balance, status, created_at, updated_at)
    VALUES (seed_tmp.code('MC', 3, 4), s, 'Vannak Kim', 0, 'ACTIVE', now() - interval '134 days', now() - interval '134 days')
    RETURNING id INTO v_card;
    UPDATE seed_tmp.people SET card_id = v_card, card_at = now() - interval '134 days' WHERE user_id = u;
    INSERT INTO member_card_top_up_requests (card_id, amount, note, receipt_url, receipt_name, status, reviewed_by, reviewed_at, created_at)
    VALUES (v_card, 300, 'Paid at the front desk', 'storage://private/receipts/' || gen_random_uuid() || '.jpg', 'receipt-front-desk.jpg',
            'APPROVED', v_admin, now() - interval '133 days', now() - interval '133 days 4 hours')
    RETURNING id INTO v_topup;
    PERFORM seed_tmp.card_tx(v_card, 'TOP_UP', 300, 'TOPUP-' || v_topup, 'Top-up request approved', v_admin, now() - interval '133 days');

    PERFORM seed_tmp.enroll(s, seed_tmp.cid('modern-javascript-from-zero'), now() - interval '130 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('modern-javascript-from-zero'), 100, now() - interval '130 days');
    PERFORM seed_tmp.review(s, seed_tmp.cid('modern-javascript-from-zero'), now() - interval '80 days', 5);
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('git-github-fundamentals'), now() - interval '125 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('git-github-fundamentals'), 6, now() - interval '125 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('nuxt-3-vue-enterprise'), now() - interval '100 days', 'fake',
                            (SELECT id FROM coupons WHERE code = 'WELCOME10'));
    PERFORM seed_tmp.progress(s, seed_tmp.cid('nuxt-3-vue-enterprise'), 9, now() - interval '100 days');
    v_order := seed_tmp.enroll(s, seed_tmp.cid('python-for-data-analysis'), now() - interval '60 days', 'member_card');
    SELECT order_number INTO v_number FROM orders WHERE id = v_order;
    PERFORM seed_tmp.card_tx(v_card, 'PURCHASE', 59.99, v_number, 'Python for Data Analysis', u, now() - interval '60 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('python-for-data-analysis'), 7, now() - interval '60 days');
    INSERT INTO member_card_top_up_requests (card_id, amount, note, status, created_at)
    VALUES (v_card, 50, 'Bank transfer from ABA, reference 88123', 'PENDING', now() - interval '1 day');
    INSERT INTO cart_items (student_id, course_id, added_at)
    VALUES (s, seed_tmp.cid('spring-boot-microservices'), now() - interval '4 days'), (s, seed_tmp.cid('llm-agents-engineering'), now() - interval '2 days');
    INSERT INTO wishlist_items (student_id, course_id, created_at)
    VALUES (s, seed_tmp.cid('docker-kubernetes-devops'), now() - interval '30 days'), (s, seed_tmp.cid('ux-research-essentials'), now() - interval '12 days');
    INSERT INTO learner_goals (student_id, weekly_lessons_target, leaderboard_visible, updated_at) VALUES (s, 5, true, now() - interval '20 days');

    -- Sreymom: a graduate. Four courses finished (the whole Frontend Developer path) and reviews
    u := seed_tmp.add_user('sreymom.chea@demo.courseaurahub.io', 'Sreymom', 'Chea', 'ROLE_USER', now() - interval '300 days', 'persona',
        'Frontend developer. Finished the Frontend Developer path.');
    s := seed_tmp.add_student(u, 'Cambodia');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('git-github-fundamentals'), now() - interval '290 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('git-github-fundamentals'), 100, now() - interval '290 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('modern-javascript-from-zero'), now() - interval '250 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('modern-javascript-from-zero'), 100, now() - interval '250 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('typescript-for-vue-devs'), now() - interval '210 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('typescript-for-vue-devs'), 100, now() - interval '210 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('nuxt-3-vue-enterprise'), now() - interval '160 days', 'fake', (SELECT id FROM coupons WHERE code = 'NUXT25'));
    PERFORM seed_tmp.progress(s, seed_tmp.cid('nuxt-3-vue-enterprise'), 100, now() - interval '160 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('ux-research-essentials'), now() - interval '40 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('ux-research-essentials'), 100, now() - interval '40 days');
    PERFORM seed_tmp.review(s, seed_tmp.cid('git-github-fundamentals'), now() - interval '240 days', 5);
    PERFORM seed_tmp.review(s, seed_tmp.cid('typescript-for-vue-devs'), now() - interval '150 days', 5);
    PERFORM seed_tmp.review(s, seed_tmp.cid('nuxt-3-vue-enterprise'), now() - interval '60 days', 4);
    PERFORM seed_tmp.review(s, seed_tmp.cid('ux-research-essentials'), now() - interval '3 days', 5);
    INSERT INTO learner_goals (student_id, weekly_lessons_target, leaderboard_visible, updated_at) VALUES (s, 10, true, now() - interval '90 days');

    -- Rithy: struggling. Out of quiz attempts (one granted back), idle in another course, failed and cancelled payments
    u := seed_tmp.add_user('rithy.pov@demo.courseaurahub.io', 'Rithy', 'Pov', 'ROLE_USER', now() - interval '75 days', 'persona');
    s := seed_tmp.add_student(u, 'Cambodia');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('python-for-data-analysis'), now() - interval '70 days', 'fake', (SELECT id FROM coupons WHERE code = 'PYTHON15'));
    -- The first section is 3 lessons; the second has 3 more before its quiz
    PERFORM seed_tmp.progress(s, seed_tmp.cid('python-for-data-analysis'), 6, now() - interval '70 days');
    INSERT INTO quiz_attempts (student_id, lesson_id, correct_count, total_count, score_percent, passed, started_at, submitted_at)
    SELECT s, l.id, x.c, 4, x.c * 25, false, now() - make_interval(days => x.d) - interval '14 minutes', now() - make_interval(days => x.d)
    FROM lessons l JOIN course_sections cs ON cs.id = l.section_id,
         (VALUES (0, 40), (1, 39), (2, 38)) AS x(c, d)
    WHERE cs.course_id = seed_tmp.cid('python-for-data-analysis') AND l.type = 'QUIZ' AND cs.position = 1;
    INSERT INTO quiz_attempt_grants (student_id, lesson_id, extra_attempts, granted_by, updated_at)
    SELECT s, l.id, 1, seed_tmp.uid('sokha.chan@demo.courseaurahub.io'), now() - interval '2 days'
    FROM lessons l JOIN course_sections cs ON cs.id = l.section_id
    WHERE cs.course_id = seed_tmp.cid('python-for-data-analysis') AND l.type = 'QUIZ' AND cs.position = 1;
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('spring-boot-microservices'), now() - interval '50 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('spring-boot-microservices'), 1, now() - interval '50 days');
    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider, checkout_url, created_at)
    VALUES (seed_tmp.code('ORD', 1, 16), s, seed_tmp.cid('docker-kubernetes-devops'), 119.99, 0, 119.99, 'USD', 'FAILED', 'fake',
            'http://localhost:3000/checkout/fake', now() - interval '20 days'),
           (seed_tmp.code('ORD', 1, 16), s, seed_tmp.cid('llm-agents-engineering'), 169.99, 0, 169.99, 'USD', 'CANCELLED', 'fake',
            'http://localhost:3000/checkout/fake', now() - interval '15 days');
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, created_at)
    VALUES (s, 'Rithy Pov', '+855 97 222 333', '1999-09-30', 'Sangkat Boeung Kak 1, Phnom Penh', 'PENDING', now() - interval '5 hours');

    -- Nita: a refunded purchase, a cart checkout waiting for payment, a pending refund request, a rejected card application
    u := seed_tmp.add_user('nita.ouk@demo.courseaurahub.io', 'Nita', 'Ouk', 'ROLE_USER', now() - interval '55 days', 'persona');
    s := seed_tmp.add_student(u, 'Thailand');
    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider,
                        provider_reference, created_at, paid_at, refunded_at)
    VALUES (seed_tmp.code('ORD', 1, 16), s, seed_tmp.cid('docker-kubernetes-devops'), 119.99, 0, 119.99, 'USD', 'REFUNDED', 'fake',
            'fake_refund_' || seed_tmp.hex(10), now() - interval '40 days', now() - interval '40 days', now() - interval '35 days')
    RETURNING id INTO v_order;
    INSERT INTO refund_requests (order_id, student_id, reason, status, auto_approved, progress_percent, decision_note, reviewed_by, reviewed_at, created_at)
    VALUES (v_order, s, 'I bought the wrong course; I meant to buy the AWS bootcamp.', 'APPROVED', false, 0,
            'Refunded in full. Enjoy the AWS course!', v_admin, now() - interval '35 days', now() - interval '36 days');
    INSERT INTO order_groups (group_number, amount, currency, created_at)
    VALUES ('GRP-' || seed_tmp.code('ORD', 1, 16), 0, 'USD', now() - interval '25 minutes') RETURNING id INTO v_group;
    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider, checkout_url, group_id, created_at)
    VALUES (seed_tmp.code('ORD', 1, 16), s, seed_tmp.cid('tailwind-css-design-systems'), 59.99, 0, 59.99, 'USD', 'PENDING', 'fake',
            'http://localhost:3000/checkout/fake', v_group, now() - interval '25 minutes'),
           (seed_tmp.code('ORD', 1, 16), s, seed_tmp.cid('llm-agents-engineering'), 169.99, 0, 169.99, 'USD', 'PENDING', 'fake',
            'http://localhost:3000/checkout/fake', v_group, now() - interval '25 minutes');
    INSERT INTO cart_items (student_id, course_id, added_at)
    VALUES (s, seed_tmp.cid('tailwind-css-design-systems'), now() - interval '1 hour'), (s, seed_tmp.cid('llm-agents-engineering'), now() - interval '1 hour');
    v_order := seed_tmp.enroll(s, seed_tmp.cid('react-19-complete-guide'), now() - interval '4 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('react-19-complete-guide'), 1, now() - interval '4 days');
    INSERT INTO refund_requests (order_id, student_id, reason, status, auto_approved, progress_percent, created_at)
    VALUES (v_order, s, 'The course is more advanced than I expected. Could I get a refund?', 'PENDING', false, 5, now() - interval '6 hours');
    INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, review_note, reviewed_by, reviewed_at, created_at)
    VALUES (s, 'Nita Ouk', '+66 81 234 5678', '2003-01-20', 'Sukhumvit Soi 11, Bangkok', 'REJECTED',
            'Please use the address on your national ID and apply again.', v_admin, now() - interval '9 days', now() - interval '10 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('ux-research-essentials'), now() - interval '8 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('ux-research-essentials'), 2, now() - interval '8 days');

    -- Bopha: just signed up, browsing and saving courses
    u := seed_tmp.add_user('bopha.lim@demo.courseaurahub.io', 'Bopha', 'Lim', 'ROLE_USER', now() - interval '1 day', 'persona');
    s := seed_tmp.add_student(u, 'Cambodia');
    INSERT INTO wishlist_items (student_id, course_id, created_at)
    VALUES (s, seed_tmp.cid('git-github-fundamentals'), now() - interval '20 hours'), (s, seed_tmp.cid('python-for-data-analysis'), now() - interval '19 hours');

    -- Kosal: all-access subscriber (section 10), Malis: organization owner (section 11), Piseth: buys gifts (section 9)
    u := seed_tmp.add_user('kosal.heng@demo.courseaurahub.io', 'Kosal', 'Heng', 'ROLE_USER', now() - interval '220 days', 'persona',
        'Data engineer. Subscribed to all-access for the AI courses.');
    PERFORM seed_tmp.add_student(u, 'Cambodia');
    u := seed_tmp.add_user('malis.tep@demo.courseaurahub.io', 'Malis', 'Tep', 'ROLE_USER', now() - interval '190 days', 'persona',
        'Engineering manager at Angkor Tech Solutions.');
    s := seed_tmp.add_student(u, 'Cambodia');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('startup-product-management'), now() - interval '150 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('startup-product-management'), 100, now() - interval '150 days');
    u := seed_tmp.add_user('piseth.ros@demo.courseaurahub.io', 'Piseth', 'Ros', 'ROLE_USER', now() - interval '160 days', 'persona');
    s := seed_tmp.add_student(u, 'Cambodia');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('aws-cloud-practitioner'), now() - interval '150 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('aws-cloud-practitioner'), 100, now() - interval '150 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('terraform-infrastructure-as-code'), now() - interval '90 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('terraform-infrastructure-as-code'), 8, now() - interval '90 days');

    -- Two-factor account (the seeder enrolls the authenticator secret), and one linked to GitHub
    u := seed_tmp.add_user('sovann.chhay@demo.courseaurahub.io', 'Sovann', 'Chhay', 'ROLE_USER', now() - interval '210 days', 'persona',
        'Security-minded learner with two-factor sign-in turned on.');
    s := seed_tmp.add_student(u, 'Cambodia');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('ethical-hacking-essentials'), now() - interval '200 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('ethical-hacking-essentials'), 100, now() - interval '200 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('cloud-security-devsecops'), now() - interval '70 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('cloud-security-devsecops'), 7, now() - interval '70 days');
    u := seed_tmp.add_user('chantha.mao@demo.courseaurahub.io', 'Chantha', 'Mao', 'ROLE_USER', now() - interval '110 days', 'persona',
        'Signs in with a password or GitHub.');
    s := seed_tmp.add_student(u, 'Laos');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('nodejs-rest-apis'), now() - interval '100 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('nodejs-rest-apis'), 12, now() - interval '100 days');
    PERFORM seed_tmp.enroll(s, seed_tmp.cid('git-github-fundamentals'), now() - interval '105 days');
    PERFORM seed_tmp.progress(s, seed_tmp.cid('git-github-fundamentals'), 100, now() - interval '105 days');

    -- Accounts that can't sign in: never verified, disabled by an admin, locked after failed attempts
    PERFORM seed_tmp.add_user('pending.signup@demo.courseaurahub.io', 'Pending', 'Signup', 'ROLE_USER', now() - interval '2 hours', 'persona',
        NULL, false, true);
    u := seed_tmp.add_user('disabled.account@demo.courseaurahub.io', 'Disabled', 'Account', 'ROLE_USER', now() - interval '150 days', 'persona',
        NULL, true, false);
    s := seed_tmp.add_student(u, 'Cambodia');
    UPDATE students SET status = 'INACTIVE' WHERE id = s;
    u := seed_tmp.add_user('locked.account@demo.courseaurahub.io', 'Locked', 'Account', 'ROLE_USER', now() - interval '90 days', 'persona');
    UPDATE users SET failed_login_attempts = 5, locked_until = now() + interval '1 day' WHERE id = u;
    PERFORM seed_tmp.add_student(u, 'Vietnam');
END $seed$;

-- ===============================================================================================================
-- 5. Bulk learners: 180 accounts with random but consistent journeys
-- ===============================================================================================================
DO $seed$
DECLARE
    v_firsts text[] := ARRAY['Sophea', 'Dara', 'Vuthy', 'Chenda', 'Rachana', 'Sopheak', 'Kimly', 'Pisey', 'Makara', 'Sothea',
                             'Bunthoeun', 'Chanthy', 'Davy', 'Leakhena', 'Mony', 'Narith', 'Phalla', 'Raksmey', 'Samnang', 'Sreyleak',
                             'Thida', 'Vibol', 'Yuthea', 'Linh', 'Minh', 'Anh', 'Somchai', 'Nok', 'Aiko', 'Daniel'];
    v_lasts text[] := ARRAY['Sok', 'Chhim', 'Keo', 'Heng', 'Ly', 'Meas', 'Nhem', 'Ouk', 'Pen', 'Ros',
                            'Sam', 'Seng', 'Tep', 'Touch', 'Var', 'Yim', 'Nguyen', 'Tran', 'Srisuk', 'Tanaka'];
    v_countries text[] := ARRAY['Cambodia', 'Cambodia', 'Cambodia', 'Cambodia', 'Cambodia', 'Cambodia', 'Cambodia', 'Thailand',
                                'Vietnam', 'Vietnam', 'Laos', 'Malaysia', 'Singapore', 'Philippines', 'Indonesia', 'Japan',
                                'South Korea', 'Australia', 'United States', 'France'];
    v_addresses text[] := ARRAY['Street 63, Boeung Keng Kang, Phnom Penh', 'Sangkat Tonle Bassac, Chamkarmon, Phnom Penh',
                                'Wat Bo Road, Siem Reap', 'Street 2004, Sen Sok, Phnom Penh', 'National Road 4, Sihanoukville',
                                'Street 105, Battambang', 'Street 488, Phsar Doeum Thkov, Phnom Penh'];
    v_admin uuid := seed_tmp.admin_id();
    i int;
    v_first text; v_last text; v_email text; v_provider text;
    v_user uuid; v_student int; v_joined timestamptz;
    v_n int; v_course record; v_at timestamptz; v_order int; v_total int; v_target int; v_done int;
    v_card int; v_card_at timestamptz; v_card_ok boolean; v_balance numeric; v_coupon int; v_topup int; v_amount numeric;
    v_number text; v_status text; v_r float8; v_app_at timestamptz;
BEGIN
    FOR i IN 0..179 LOOP
        v_first := v_firsts[1 + i % 30];
        v_last := v_lasts[1 + (i / 30 * 7 + i) % 20];
        v_email := lower(v_first || '.' || v_last) || '@demo.courseaurahub.io';
        v_joined := now() - make_interval(days => seed_tmp.rint(2, 365), hours => seed_tmp.rint(0, 23));
        -- A few accounts were created through Google, GitHub or Facebook and have no password
        v_provider := CASE WHEN i % 37 = 5 THEN 'GOOGLE' WHEN i % 37 = 17 THEN 'GITHUB' WHEN i % 37 = 29 THEN 'FACEBOOK' ELSE 'LOCAL' END;
        v_user := seed_tmp.add_user(v_email, v_first, v_last, 'ROLE_USER', v_joined, 'bulk', NULL, true, true, v_provider);
        v_student := seed_tmp.add_student(v_user, seed_tmp.pick(v_countries));
        IF random() < 0.06 THEN
            UPDATE students SET status = 'INACTIVE' WHERE id = v_student;
        END IF;

        -- Member card: about one in five apply
        v_card := NULL; v_balance := 0; v_card_ok := false;
        IF random() < 0.22 THEN
            v_card_at := v_joined + make_interval(days => seed_tmp.rint(1, 6));
            IF v_card_at < now() - interval '2 days' THEN
                v_r := random();
                v_status := CASE WHEN v_r < 0.76 THEN 'APPROVED' WHEN v_r < 0.86 THEN 'PENDING' ELSE 'REJECTED' END;
                v_app_at := CASE WHEN v_status = 'PENDING' THEN now() - make_interval(hours => seed_tmp.rint(2, 70)) ELSE v_card_at - interval '1 day' END;
                INSERT INTO member_card_applications (student_id, full_name, phone, date_of_birth, address, status, review_note,
                                                      reviewed_by, reviewed_at, created_at)
                VALUES (v_student, v_first || ' ' || v_last,
                        '+855 ' || seed_tmp.rint(10, 99) || ' ' || seed_tmp.rint(100, 999) || ' ' || seed_tmp.rint(100, 999),
                        date '1985-01-01' + seed_tmp.rint(0, 7000), seed_tmp.pick(v_addresses), v_status,
                        CASE WHEN v_status = 'REJECTED' THEN seed_tmp.pick(ARRAY['The phone number could not be reached.',
                            'Please upload a clearer photo of your ID.', 'Your date of birth does not match your ID.']) END,
                        CASE WHEN v_status <> 'PENDING' THEN v_admin END, CASE WHEN v_status <> 'PENDING' THEN v_card_at END, v_app_at);
                IF v_status = 'APPROVED' THEN
                    INSERT INTO member_cards (card_number, student_id, holder_name, balance, status, created_at, updated_at)
                    VALUES (seed_tmp.code('MC', 3, 4), v_student, v_first || ' ' || v_last, 0,
                            CASE WHEN random() < 0.06 THEN 'SUSPENDED' ELSE 'ACTIVE' END, v_card_at, v_card_at)
                    RETURNING id, status = 'ACTIVE' INTO v_card, v_card_ok;
                    v_amount := seed_tmp.pick(ARRAY['100', '150', '200', '300', '500'])::numeric;
                    INSERT INTO member_card_top_up_requests (card_id, amount, note, receipt_url, receipt_name, status, reviewed_by, reviewed_at, created_at)
                    VALUES (v_card, v_amount, seed_tmp.pick(ARRAY['Paid at the front desk', 'ABA transfer', 'Wing transfer', 'ACLEDA deposit']),
                            'storage://private/receipts/' || gen_random_uuid() || '.jpg', 'receipt.jpg', 'APPROVED', v_admin,
                            v_card_at + interval '20 hours', v_card_at + interval '2 hours')
                    RETURNING id INTO v_topup;
                    PERFORM seed_tmp.card_tx(v_card, 'TOP_UP', v_amount, 'TOPUP-' || v_topup, 'Top-up request approved', v_admin,
                                             v_card_at + interval '20 hours');
                    v_balance := v_amount;
                    v_card_at := v_card_at + interval '21 hours';
                    UPDATE seed_tmp.people SET card_id = v_card, card_at = v_card_at WHERE user_id = v_user;
                END IF;
            END IF;
        END IF;

        -- Courses, weighted by popularity
        v_n := (ARRAY[0, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6, 7, 8])[seed_tmp.rint(1, 16)];
        FOR v_course IN
            SELECT co.id, co.price, co.created_at::timestamptz AS created
            FROM seed_tmp.catalog c JOIN courses co ON co.id = c.course_id
            WHERE co.status = 'PUBLISHED'
            ORDER BY -ln(1 - random()) / c.popularity
            LIMIT v_n
        LOOP
            v_at := greatest(v_joined, v_course.created + interval '1 day');
            CONTINUE WHEN v_at > now() - interval '3 hours';
            v_at := v_at + random() * (now() - interval '2 hours' - v_at);
            IF v_course.price > 0 AND v_card_ok AND v_at > v_card_at AND v_balance >= v_course.price AND random() < 0.55 THEN
                v_order := seed_tmp.enroll(v_student, v_course.id, v_at, 'member_card');
                SELECT order_number INTO v_number FROM orders WHERE id = v_order;
                PERFORM seed_tmp.card_tx(v_card, 'PURCHASE', v_course.price, v_number, (SELECT title FROM courses WHERE id = v_course.id), v_user, v_at);
                v_balance := v_balance - v_course.price;
            ELSE
                v_coupon := CASE WHEN v_course.price > 0 AND random() < 0.2 THEN seed_tmp.valid_coupon(v_course.id, v_at) END;
                PERFORM seed_tmp.enroll(v_student, v_course.id, v_at, 'fake', v_coupon);
            END IF;

            SELECT count(*) INTO v_total FROM lessons l JOIN course_sections s ON s.id = l.section_id WHERE s.course_id = v_course.id;
            v_r := random();
            v_target := CASE WHEN v_r < 0.14 THEN 0 WHEN v_r < 0.55 THEN seed_tmp.rint(1, greatest(v_total - 1, 1)) ELSE v_total END;
            v_done := seed_tmp.progress(v_student, v_course.id, v_target, v_at);
            IF v_done >= 2 AND random() < 0.5 THEN
                PERFORM seed_tmp.review(v_student, v_course.id,
                    (SELECT max(lp.completed_at) FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id
                     JOIN course_sections s ON s.id = l.section_id WHERE lp.student_id = v_student AND s.course_id = v_course.id)
                    + make_interval(hours => seed_tmp.rint(2, 72)));
            END IF;
        END LOOP;

        IF random() < 0.35 THEN
            INSERT INTO wishlist_items (student_id, course_id, created_at)
            SELECT v_student, co.id, v_joined + random() * (now() - v_joined) FROM courses co
            WHERE co.status = 'PUBLISHED'
              AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = v_student AND sc.course_id = co.id)
            ORDER BY random() LIMIT seed_tmp.rint(1, 3);
        END IF;
        IF random() < 0.12 THEN
            INSERT INTO cart_items (student_id, course_id, added_at)
            SELECT v_student, co.id, now() - make_interval(hours => seed_tmp.rint(1, 400)) FROM courses co
            WHERE co.status = 'PUBLISHED' AND co.price > 0
              AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = v_student AND sc.course_id = co.id)
            ORDER BY random() LIMIT seed_tmp.rint(1, 2);
        END IF;
        IF random() < 0.55 THEN
            INSERT INTO learner_goals (student_id, weekly_lessons_target, leaderboard_visible, updated_at)
            VALUES (v_student, CASE WHEN random() < 0.85 THEN seed_tmp.rint(2, 12) END, random() > 0.1,
                    v_joined + random() * (now() - v_joined));
        END IF;
    END LOOP;
END $seed$;

-- A few learners enrolled before the jQuery course was archived; they keep access
DO $seed$
DECLARE
    v_s int;
BEGIN
    FOR v_s IN SELECT student_id FROM seed_tmp.people WHERE kind = 'bulk' ORDER BY random() LIMIT 9 LOOP
        PERFORM seed_tmp.enroll(v_s, seed_tmp.cid('jquery-legacy-web'), now() - make_interval(days => seed_tmp.rint(200, 360)));
        PERFORM seed_tmp.progress(v_s, seed_tmp.cid('jquery-legacy-web'), seed_tmp.rint(1, 12), now() - make_interval(days => seed_tmp.rint(150, 199)));
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 6. Orders in every status, cart checkouts, refunds and refund requests
-- ===============================================================================================================
DO $seed$
DECLARE
    v_admin uuid := seed_tmp.admin_id();
    v_row record;
    v_group int;
    v_at timestamptz;
    v_card int;
    v_done int;
    v_total int;
BEGIN
    -- Failed and cancelled checkouts
    INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider,
                        checkout_url, created_at)
    SELECT seed_tmp.code('ORD', 1, 16), p.student_id, c.id, c.price, 0, c.price, 'USD',
           CASE WHEN random() < 0.5 THEN 'FAILED' ELSE 'CANCELLED' END, 'fake', 'http://localhost:3000/checkout/fake',
           now() - make_interval(days => seed_tmp.rint(1, 300), hours => seed_tmp.rint(0, 23))
    FROM (SELECT student_id FROM seed_tmp.people WHERE kind = 'bulk' ORDER BY random() LIMIT 55) p
    CROSS JOIN LATERAL (SELECT co.id, co.price FROM courses co WHERE co.status = 'PUBLISHED' AND co.price > 0
                          AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = p.student_id AND sc.course_id = co.id)
                        ORDER BY random() LIMIT 1) c;

    -- Cart checkouts waiting for payment: every cart item of a few learners in one group
    FOR v_row IN
        SELECT ci.student_id FROM cart_items ci JOIN seed_tmp.people p ON p.student_id = ci.student_id AND p.kind = 'bulk'
        GROUP BY ci.student_id HAVING count(*) >= 1 ORDER BY random() LIMIT 8
    LOOP
        v_at := now() - make_interval(mins => seed_tmp.rint(5, 600));
        INSERT INTO order_groups (group_number, amount, currency, created_at)
        VALUES ('GRP-' || seed_tmp.code('ORD', 1, 16), 0, 'USD', v_at) RETURNING id INTO v_group;
        INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider,
                            checkout_url, group_id, created_at)
        SELECT seed_tmp.code('ORD', 1, 16), ci.student_id, co.id, co.price, 0, co.price, 'USD', 'PENDING', 'fake',
               'http://localhost:3000/checkout/fake', v_group, v_at
        FROM cart_items ci JOIN courses co ON co.id = ci.course_id WHERE ci.student_id = v_row.student_id;
    END LOOP;
    -- Paid cart checkouts from the past: group a few same-day paid orders
    FOR v_row IN
        SELECT o.student_id, o.paid_at::date AS day FROM orders o
        WHERE o.status = 'PAID' AND o.group_id IS NULL AND o.provider = 'fake'
        GROUP BY o.student_id, o.paid_at::date HAVING count(*) >= 2 LIMIT 15
    LOOP
        INSERT INTO order_groups (group_number, amount, currency, created_at)
        VALUES ('GRP-' || seed_tmp.code('ORD', 1, 16), 0, 'USD', v_row.day::timestamptz + interval '9 hours') RETURNING id INTO v_group;
        UPDATE orders SET group_id = v_group
        WHERE student_id = v_row.student_id AND paid_at::date = v_row.day AND status = 'PAID' AND provider = 'fake';
    END LOOP;
    UPDATE order_groups g SET amount = coalesce((SELECT sum(amount) FROM orders o WHERE o.group_id = g.id), 0);

    -- Refunds: early in the course, most through an approved request, two long after the sale
    FOR v_row IN
        SELECT o.*, (SELECT count(*) FROM lessons l JOIN course_sections s ON s.id = l.section_id WHERE s.course_id = o.course_id) AS total,
               (SELECT count(*) FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id JOIN course_sections s ON s.id = l.section_id
                WHERE lp.student_id = o.student_id AND s.course_id = o.course_id) AS done
        FROM orders o JOIN seed_tmp.people p ON p.student_id = o.student_id AND p.kind = 'bulk'
        WHERE o.status = 'PAID' AND o.amount > 0 AND NOT o.gift AND o.group_id IS NULL AND o.paid_at < now() - interval '70 days'
          AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = o.student_id AND sc.course_id = o.course_id AND sc.completed)
        ORDER BY random() LIMIT 30
    LOOP
        v_done := v_row.done;
        v_total := greatest(v_row.total, 1);
        CONTINUE WHEN v_done * 100 / v_total > 40;
        v_at := v_row.paid_at + CASE WHEN random() < 0.1 THEN interval '35 days' ELSE make_interval(days => seed_tmp.rint(1, 9)) END;
        UPDATE orders SET status = 'REFUNDED', refunded_at = v_at WHERE id = v_row.id;
        IF random() < 0.8 THEN
            INSERT INTO refund_requests (order_id, student_id, reason, status, auto_approved, progress_percent, decision_note,
                                         reviewed_by, reviewed_at, created_at)
            VALUES (v_row.id, v_row.student_id, seed_tmp.pick(ARRAY['The course was not what I expected.',
                        'I bought it by mistake.', 'I do not have time to study right now.', 'The level is too advanced for me.',
                        'My company is paying for a different course.']),
                    'APPROVED', v_done = 0, v_done * 100 / v_total,
                    CASE WHEN v_done = 0 THEN NULL ELSE 'Approved. Sorry it was not the right fit.' END,
                    CASE WHEN v_done = 0 THEN NULL ELSE v_admin END, v_at, v_at - interval '1 day');
        END IF;
        IF v_row.provider = 'member_card' THEN
            SELECT id INTO v_card FROM member_cards WHERE student_id = v_row.student_id;
            PERFORM seed_tmp.card_tx(v_card, 'REFUND', v_row.amount, v_row.order_number, 'Refund', v_admin, v_at);
        END IF;
        -- EnrollmentService.delete: the enrollment goes, its review and progress with it
        DELETE FROM course_reviews WHERE student_id = v_row.student_id AND course_id = v_row.course_id;
        DELETE FROM lesson_progress lp USING lessons l, course_sections s
        WHERE lp.lesson_id = l.id AND l.section_id = s.id AND s.course_id = v_row.course_id AND lp.student_id = v_row.student_id;
        DELETE FROM enrollments WHERE student_id = v_row.student_id AND course_id = v_row.course_id;
        DELETE FROM student_courses WHERE student_id = v_row.student_id AND course_id = v_row.course_id;
    END LOOP;

    -- Open and rejected refund requests on paid orders
    INSERT INTO refund_requests (order_id, student_id, reason, status, auto_approved, progress_percent, decision_note, reviewed_by, reviewed_at, created_at)
    SELECT o.id, o.student_id,
           seed_tmp.pick(ARRAY['The videos do not play well on my connection.', 'I found the same content for free.',
                               'I expected more hands-on projects.', 'Please refund, I enrolled in the wrong course.']),
           x.status, false, seed_tmp.rint(0, 35),
           CASE WHEN x.status = 'REJECTED' THEN seed_tmp.pick(ARRAY['You completed most of the course, which is past our refund policy.',
                                                                     'The refund window of 30 days has passed.']) END,
           CASE WHEN x.status = 'REJECTED' THEN v_admin END,
           CASE WHEN x.status = 'REJECTED' THEN o.paid_at + interval '3 days' END,
           CASE WHEN x.status = 'REJECTED' THEN o.paid_at + interval '2 days' ELSE now() - make_interval(hours => seed_tmp.rint(1, 60)) END
    FROM (SELECT o.*, row_number() OVER (ORDER BY random()) AS rn FROM orders o
          WHERE o.status = 'PAID' AND o.amount > 0 AND NOT o.gift
            AND NOT EXISTS (SELECT 1 FROM refund_requests r WHERE r.order_id = o.id)) o
    CROSS JOIN LATERAL (SELECT CASE WHEN o.rn <= 8 THEN 'PENDING' ELSE 'REJECTED' END AS status) x
    WHERE o.rn <= 15;
END $seed$;

-- ===============================================================================================================
-- 7. Gifts and access codes
-- ===============================================================================================================
DO $seed$
DECLARE
    v_admin uuid := seed_tmp.admin_id();
    v_buyer record;
    v_course record;
    v_order int;
    v_code text;
    v_code_id int;
    v_recipient record;
    v_at timestamptz;
    v_known boolean;
BEGIN
    -- Gift purchases: Piseth plus a dozen learners. The first gift's code is a known one for trying redemption.
    FOR v_buyer IN
        (SELECT p.*, 0 AS o FROM seed_tmp.people p WHERE p.email = 'piseth.ros@demo.courseaurahub.io')
        UNION ALL
        (SELECT p.*, 1 FROM seed_tmp.people p WHERE p.kind = 'bulk' ORDER BY random() LIMIT 12)
        ORDER BY o
    LOOP
        FOR k IN 1..CASE WHEN v_buyer.o = 0 THEN 3 ELSE 1 END LOOP
            SELECT co.id, co.price, co.title INTO v_course FROM courses co WHERE co.status = 'PUBLISHED' AND co.price > 0 ORDER BY random() LIMIT 1;
            v_at := greatest(v_buyer.joined, now() - interval '200 days') + random() * (now() - interval '1 day' - greatest(v_buyer.joined, now() - interval '200 days'));
            INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider,
                                provider_reference, created_at, paid_at, gift)
            VALUES (seed_tmp.code('ORD', 1, 16), v_buyer.student_id, v_course.id, v_course.price, 0, v_course.price, 'USD', 'PAID', 'fake',
                    'fake_gift_' || seed_tmp.hex(10), v_at - interval '3 minutes', v_at, true)
            RETURNING id INTO v_order;
            v_known := v_buyer.o = 0 AND k = 1;
            v_code := CASE WHEN v_known THEN 'DEM0-G1FT-2026-0001' ELSE seed_tmp.code('', 4, 4) END;
            SELECT p.* INTO v_recipient FROM seed_tmp.people p
            WHERE p.kind = 'bulk' AND p.student_id IS NOT NULL AND p.student_id <> v_buyer.student_id
              AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = p.student_id AND sc.course_id = v_course.id)
            ORDER BY random() LIMIT 1;
            INSERT INTO access_codes (code_hash, code_hint, course_id, batch, max_uses, uses, expires_at, revoked, created_by,
                                      gift_order_id, purchaser_student_id, recipient_email, message, created_at)
            VALUES ('seed:' || v_code, right(v_code, 4), v_course.id, NULL, 1, 0, v_at + interval '365 days', false, v_buyer.user_id,
                    v_order, v_buyer.student_id,
                    CASE WHEN v_known THEN 'bopha.lim@demo.courseaurahub.io' ELSE v_recipient.email END,
                    seed_tmp.pick(ARRAY['Happy birthday! Enjoy the course.', 'Congrats on the new job, this will help!',
                                        'For our study group. See you in class!', 'You talked about this for months, now go learn it :)']),
                    v_at)
            RETURNING id INTO v_code_id;
            IF NOT v_known AND random() < 0.65 AND v_recipient.student_id IS NOT NULL THEN
                PERFORM seed_tmp.enroll(v_recipient.student_id, v_course.id, v_at + interval '1 day', 'fake', NULL, false);
                INSERT INTO access_code_redemptions (code_id, student_id, redeemed_at) VALUES (v_code_id, v_recipient.student_id, v_at + interval '1 day');
                PERFORM seed_tmp.progress(v_recipient.student_id, v_course.id, seed_tmp.rint(0, 10), v_at + interval '1 day');
            END IF;
        END LOOP;
    END LOOP;

    -- Batches created by admins for workshops and partners
    INSERT INTO access_codes (code_hash, code_hint, course_id, batch, max_uses, uses, expires_at, revoked, created_by, created_at)
    VALUES ('seed:CAMP-2026-0000-0001', '0001', seed_tmp.cid('docker-kubernetes-devops'), 'BOOTCAMP-2026', 30, 0, now() + interval '60 days', false, v_admin, now() - interval '45 days'),
           ('seed:' || seed_tmp.code('', 4, 4), 'PRTN', seed_tmp.cid('python-for-data-analysis'), 'PARTNER-ABA', 100, 0, now() + interval '200 days', false, v_admin, now() - interval '90 days'),
           ('seed:' || seed_tmp.code('', 4, 4), 'REVK', seed_tmp.cid('react-19-complete-guide'), 'MEETUP-OCT', 20, 0, now() + interval '20 days', true, v_admin, now() - interval '50 days'),
           ('seed:' || seed_tmp.code('', 4, 4), 'EXPD', seed_tmp.cid('sql-for-analysts'), 'UNIVERSITY-2025', 50, 0, now() - interval '30 days', false, v_admin, now() - interval '200 days');
    UPDATE access_codes SET code_hint = right(substr(code_hash, 6), 4) WHERE batch IS NOT NULL;
    FOR v_recipient IN
        SELECT p.student_id, ac.id AS code_id, ac.course_id, ac.created_at AS code_at
        FROM access_codes ac
        CROSS JOIN LATERAL (SELECT p.student_id FROM seed_tmp.people p WHERE p.kind = 'bulk'
                              AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = p.student_id AND sc.course_id = ac.course_id)
                            ORDER BY random() LIMIT CASE ac.batch WHEN 'BOOTCAMP-2026' THEN 8 WHEN 'PARTNER-ABA' THEN 3 ELSE 6 END) p
        WHERE ac.batch IN ('BOOTCAMP-2026', 'PARTNER-ABA', 'UNIVERSITY-2025')
    LOOP
        v_at := v_recipient.code_at + random() * least(now() - v_recipient.code_at, interval '30 days');
        PERFORM seed_tmp.enroll(v_recipient.student_id, v_recipient.course_id, v_at, 'fake', NULL, false);
        INSERT INTO access_code_redemptions (code_id, student_id, redeemed_at) VALUES (v_recipient.code_id, v_recipient.student_id, v_at);
        PERFORM seed_tmp.progress(v_recipient.student_id, v_recipient.course_id, seed_tmp.rint(0, 14), v_at);
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 8. Subscriptions: plans, subscribers in every status, period payments, enrollments through the plan
-- ===============================================================================================================
INSERT INTO subscription_plans (code, name, description, price, billing_interval, active, created_at, updated_at) VALUES
    ('all-access-monthly', 'All-Access Monthly', 'Every published course while you stay subscribed. Cancel any time.', 29.00, 'MONTH', true, now() - interval '330 days', now() - interval '60 days'),
    ('all-access-yearly', 'All-Access Yearly', 'Every published course for a year, two months free compared with monthly.', 249.00, 'YEAR', true, now() - interval '330 days', now() - interval '60 days'),
    ('team-monthly', 'Team Monthly (retired)', 'The old team plan. Existing subscribers moved to organization seats.', 99.00, 'MONTH', false, now() - interval '320 days', now() - interval '100 days');

DO $seed$
DECLARE
    v_row record;
    v_plan record;
    v_code text;
    v_sub int;
    v_status text;
    v_start timestamptz;
    v_end_at timestamptz;
    v_period_start timestamptz;
    v_step interval;
    v_n int;
    v_course record;
    v_at timestamptz;
    v_idx int := 0;
BEGIN
    FOR v_row IN
        (SELECT p.*, 0 AS o FROM seed_tmp.people p WHERE p.email = 'kosal.heng@demo.courseaurahub.io')
        UNION ALL
        (SELECT p.*, 1 FROM seed_tmp.people p WHERE p.kind = 'bulk' AND p.joined < now() - interval '40 days' ORDER BY random() LIMIT 34)
        ORDER BY o
    LOOP
        v_idx := v_idx + 1;
        v_status := CASE WHEN v_row.o = 0 THEN 'ACTIVE'
                         ELSE (ARRAY['ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE', 'ACTIVE',
                                     'PAST_DUE', 'PAST_DUE', 'PENDING', 'PENDING', 'CANCELLED', 'CANCELLED', 'CANCELLED',
                                     'EXPIRED', 'EXPIRED', 'EXPIRED'])[1 + v_idx % 20] END;
        v_code := CASE WHEN v_row.o = 0 OR (v_status = 'ACTIVE' AND random() < 0.3) THEN 'all-access-yearly' ELSE 'all-access-monthly' END;
        SELECT * INTO v_plan FROM subscription_plans WHERE code = v_code;
        v_step := CASE v_plan.billing_interval WHEN 'YEAR' THEN interval '1 year' ELSE interval '1 month' END;

        IF v_status = 'PENDING' THEN
            -- Started checkout two hours ago and hasn't paid yet
            INSERT INTO subscriptions (student_id, plan_id, status, provider, created_at, updated_at)
            VALUES (v_row.student_id, v_plan.id, 'PENDING', 'fake', now() - interval '2 hours', now() - interval '2 hours')
            RETURNING id INTO v_sub;
            INSERT INTO subscription_payments (payment_number, subscription_id, amount, currency, status, period_start, period_end,
                                               provider, checkout_url, created_at)
            VALUES (seed_tmp.code('SUB', 1, 16), v_sub, v_plan.price, 'USD', 'PENDING', now() - interval '2 hours',
                    now() - interval '2 hours' + v_step, 'fake', 'http://localhost:3000/checkout/fake', now() - interval '2 hours');
            CONTINUE;
        END IF;

        -- Live plans run to now; a past-due one's last period ended a day ago (inside the 3-day grace); ended ones stopped earlier
        v_end_at := CASE v_status WHEN 'ACTIVE' THEN now() WHEN 'PAST_DUE' THEN now() - interval '1 day'
                                  ELSE now() - make_interval(days => seed_tmp.rint(5, 40)) END;
        IF v_status = 'ACTIVE' THEN
            v_start := CASE WHEN v_row.o = 0 THEN now() - interval '150 days'
                            ELSE v_row.joined + interval '2 days' + random() * (now() - interval '3 days' - v_row.joined) END;
        ELSE
            v_n := seed_tmp.rint(1, 6);
            v_start := v_end_at - v_n * v_step;
            WHILE v_start < v_row.joined + interval '1 day' AND v_n > 1 LOOP
                v_n := v_n - 1;
                v_start := v_end_at - v_n * v_step;
            END LOOP;
        END IF;
        INSERT INTO subscriptions (student_id, plan_id, status, provider, created_at, updated_at)
        VALUES (v_row.student_id, v_plan.id, v_status, 'fake', v_start - interval '5 minutes', now() - make_interval(days => seed_tmp.rint(0, 5)))
        RETURNING id INTO v_sub;

        v_period_start := v_start;
        WHILE v_period_start < v_end_at - interval '1 hour' LOOP
            INSERT INTO subscription_payments (payment_number, subscription_id, amount, currency, status, period_start, period_end,
                                               provider, provider_reference, created_at, paid_at)
            VALUES (seed_tmp.code('SUB', 1, 16), v_sub, v_plan.price, 'USD', 'PAID', v_period_start, v_period_start + v_step, 'fake',
                    'fake_sub_' || seed_tmp.hex(12), v_period_start - interval '2 minutes', v_period_start);
            v_period_start := v_period_start + v_step;
        END LOOP;
        -- v_period_start is now the end of the last paid period
        IF v_status IN ('PAST_DUE', 'EXPIRED') THEN
            INSERT INTO subscription_payments (payment_number, subscription_id, amount, currency, status, period_start, period_end,
                                               provider, provider_reference, created_at)
            VALUES (seed_tmp.code('SUB', 1, 16), v_sub, v_plan.price, 'USD', 'FAILED', v_period_start, v_period_start + v_step, 'fake',
                    'fake_sub_' || seed_tmp.hex(12), v_period_start + interval '1 hour');
        END IF;
        UPDATE subscriptions SET current_period_start = v_period_start - v_step, current_period_end = v_period_start,
               cancel_at_period_end = (v_status = 'ACTIVE' AND v_row.o = 1 AND random() < 0.15),
               cancelled_at = CASE WHEN v_status = 'CANCELLED' THEN v_period_start - interval '10 days' END
        WHERE id = v_sub;

        -- Live subscribers enroll in courses through the plan; ended plans already lost those enrollments
        IF v_status IN ('ACTIVE', 'PAST_DUE') THEN
            FOR v_course IN
                SELECT co.id FROM courses co
                WHERE co.status = 'PUBLISHED' AND co.price > 0
                  AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = v_row.student_id AND sc.course_id = co.id)
                  AND (v_row.o = 1 OR co.slug IN ('python-for-data-analysis', 'machine-learning-foundations', 'llm-agents-engineering', 'docker-kubernetes-devops'))
                ORDER BY random() LIMIT CASE WHEN v_row.o = 0 THEN 4 ELSE seed_tmp.rint(1, 5) END
            LOOP
                v_at := v_start + random() * (least(v_end_at, now()) - interval '1 hour' - v_start);
                PERFORM seed_tmp.enroll(v_row.student_id, v_course.id, v_at, 'fake', NULL, false);
                INSERT INTO subscription_enrollments (subscription_id, student_id, course_id, created_at)
                VALUES (v_sub, v_row.student_id, v_course.id, v_at);
                PERFORM seed_tmp.progress(v_row.student_id, v_course.id, CASE WHEN v_row.o = 0 THEN 100 ELSE seed_tmp.rint(0, 25) END, v_at);
            END LOOP;
        END IF;
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 9. Organizations: members, seats, seat assignments, invites
-- ===============================================================================================================
DO $seed$
DECLARE
    v_org record;
    v_org_id int;
    v_owner uuid;
    v_member record;
    v_seat record;
    v_seat_id int;
    v_at timestamptz;
BEGIN
    FOR v_org IN SELECT * FROM (VALUES
        ('angkor-tech-solutions', 'Angkor Tech Solutions', 'malis.tep@demo.courseaurahub.io', 170, 12,
         ARRAY['docker-kubernetes-devops', 'spring-boot-microservices', 'react-19-complete-guide']),
        ('mekong-bank-academy', 'Mekong Bank Academy', NULL, 140, 10, ARRAY['python-for-data-analysis', 'sql-for-analysts', 'ethical-hacking-essentials']),
        ('pp-design-studio', 'Phnom Penh Design Studio', NULL, 90, 6, ARRAY['ux-research-essentials', 'figma-prototyping-masterclass'])
    ) AS o(slug, name, owner_email, days, members, courses)
    LOOP
        v_owner := coalesce(seed_tmp.uid(v_org.owner_email),
                            (SELECT user_id FROM seed_tmp.people WHERE kind = 'bulk' AND joined < now() - make_interval(days => v_org.days)
                             AND user_id NOT IN (SELECT user_id FROM organization_members) ORDER BY random() LIMIT 1));
        INSERT INTO organizations (slug, name, created_by, created_at)
        VALUES (v_org.slug, v_org.name, v_owner, now() - make_interval(days => v_org.days)) RETURNING id INTO v_org_id;
        INSERT INTO organization_members (organization_id, user_id, role, joined_at) VALUES (v_org_id, v_owner, 'OWNER', now() - make_interval(days => v_org.days));
        INSERT INTO organization_members (organization_id, user_id, role, joined_at)
        SELECT v_org_id, p.user_id, CASE WHEN row_number() OVER () <= 2 THEN 'MANAGER' ELSE 'MEMBER' END,
               now() - make_interval(days => seed_tmp.rint(5, v_org.days - 1))
        FROM (SELECT user_id FROM seed_tmp.people WHERE kind = 'bulk' AND student_id IS NOT NULL
              AND user_id NOT IN (SELECT user_id FROM organization_members) ORDER BY random() LIMIT v_org.members) p;

        FOR v_seat IN SELECT seed_tmp.cid(x) AS course_id FROM unnest(v_org.courses) AS x LOOP
            INSERT INTO organization_seats (organization_id, course_id, total, created_at)
            VALUES (v_org_id, v_seat.course_id, v_org.members + seed_tmp.rint(2, 10), now() - make_interval(days => v_org.days - 2))
            RETURNING id INTO v_seat_id;
            FOR v_member IN
                SELECT m.user_id, m.joined_at, s.id AS student_id FROM organization_members m JOIN students s ON s.user_id = m.user_id
                WHERE m.organization_id = v_org_id AND random() < 0.7
                  AND NOT EXISTS (SELECT 1 FROM student_courses sc WHERE sc.student_id = s.id AND sc.course_id = v_seat.course_id)
            LOOP
                v_at := v_member.joined_at + interval '1 day';
                CONTINUE WHEN v_at > now() - interval '1 hour';
                INSERT INTO organization_seat_assignments (seat_id, user_id, assigned_by, assigned_at) VALUES (v_seat_id, v_member.user_id, v_owner, v_at);
                PERFORM seed_tmp.enroll(v_member.student_id, v_seat.course_id, v_at, 'fake', NULL, false);
                PERFORM seed_tmp.progress(v_member.student_id, v_seat.course_id, seed_tmp.rint(0, 22), v_at);
            END LOOP;
        END LOOP;

        INSERT INTO organization_invites (organization_id, email, role, token_hash, invited_by, expires_at, accepted_at, created_at) VALUES
            (v_org_id, 'new.hire@' || replace(v_org.slug, '-', '') || '.example', 'MEMBER',
             'seed:' || CASE WHEN v_org.slug = 'angkor-tech-solutions' THEN 'angkor-invite-demo-token-2026' ELSE seed_tmp.hex(32) END,
             v_owner, now() + interval '12 days', NULL, now() - interval '2 days'),
            (v_org_id, 'team.lead@' || replace(v_org.slug, '-', '') || '.example', 'MANAGER', 'seed:' || seed_tmp.hex(32), v_owner,
             now() + interval '5 days', NULL, now() - interval '9 days'),
            (v_org_id, 'old.invite@' || replace(v_org.slug, '-', '') || '.example', 'MEMBER', 'seed:' || seed_tmp.hex(32), v_owner,
             now() - interval '20 days', NULL, now() - interval '34 days');
        INSERT INTO organization_invites (organization_id, email, role, token_hash, invited_by, expires_at, accepted_at, created_at)
        SELECT v_org_id, u.email, m.role, 'seed:' || seed_tmp.hex(32), v_owner, m.joined_at + interval '13 days', m.joined_at,
               m.joined_at - interval '1 day'
        FROM organization_members m JOIN users u ON u.id = m.user_id
        WHERE m.organization_id = v_org_id AND m.role <> 'OWNER';
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 10. Referrals: codes, sign-ups, qualified and rewarded referrals (rewards credit member cards)
-- ===============================================================================================================
DO $seed$
DECLARE
    v_alphabet constant text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    v_row record;
    v_referrer record;
    v_paid record;
    v_ref int;
    v_reward_at timestamptz;
BEGIN
    INSERT INTO referral_codes (user_id, code, created_at)
    SELECT p.user_id,
           CASE WHEN p.email = 'vannak.kim@demo.courseaurahub.io' THEN 'VANNAK26'
                ELSE (SELECT string_agg(substr(v_alphabet, 1 + floor(random() * 31)::int, 1), '') FROM generate_series(1, 8 + (p.joined IS NULL)::int * 0)) END,
           p.joined + interval '1 day'
    FROM seed_tmp.people p
    WHERE p.student_id IS NOT NULL AND p.email NOT LIKE 'pending.%' AND p.email NOT LIKE 'disabled.%'
      AND (p.kind = 'persona' OR p.card_id IS NOT NULL OR random() < 0.25);

    FOR v_row IN
        SELECT p.* FROM seed_tmp.people p
        WHERE p.kind = 'bulk' AND p.joined > now() - interval '300 days' ORDER BY random() LIMIT 45
    LOOP
        SELECT p.* INTO v_referrer FROM seed_tmp.people p JOIN referral_codes rc ON rc.user_id = p.user_id
        WHERE p.joined < v_row.joined - interval '5 days' AND p.user_id <> v_row.user_id
        ORDER BY (p.email = 'vannak.kim@demo.courseaurahub.io') DESC, (p.card_id IS NOT NULL) DESC, random() LIMIT 1;
        CONTINUE WHEN v_referrer.user_id IS NULL;
        SELECT o.id, o.paid_at INTO v_paid FROM orders o
        WHERE o.student_id = v_row.student_id AND o.status IN ('PAID', 'REFUNDED') AND o.amount > 0 ORDER BY o.paid_at LIMIT 1;
        INSERT INTO referrals (referrer_user_id, referee_user_id, status, created_at)
        VALUES (v_referrer.user_id, v_row.user_id, 'SIGNED_UP', v_row.joined + interval '2 minutes') RETURNING id INTO v_ref;
        IF v_paid.id IS NOT NULL THEN
            v_reward_at := greatest(v_paid.paid_at + interval '14 days', coalesce(v_referrer.card_at, now()) + interval '1 hour');
            IF v_referrer.card_id IS NOT NULL AND v_reward_at < now() - interval '1 hour'
               AND (SELECT status FROM orders WHERE id = v_paid.id) = 'PAID' THEN
                UPDATE referrals SET status = 'REWARDED', order_id = v_paid.id, reward_amount = 5.00, qualified_at = v_paid.paid_at,
                                     rewarded_at = v_reward_at WHERE id = v_ref;
                PERFORM seed_tmp.card_tx(v_referrer.card_id, 'TOP_UP', 5.00, 'REFERRAL-' || v_ref, 'Referral reward', NULL, v_reward_at);
            ELSIF (SELECT status FROM orders WHERE id = v_paid.id) = 'PAID' THEN
                UPDATE referrals SET status = 'QUALIFIED', order_id = v_paid.id, reward_amount = 5.00, qualified_at = v_paid.paid_at WHERE id = v_ref;
            END IF;
        END IF;
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 11. Member card extras: later top-ups in every state, admin adjustments, then balances from the ledgers
-- ===============================================================================================================
DO $seed$
DECLARE
    v_admin uuid := seed_tmp.admin_id();
    v_card record;
    v_req int;
    v_amount numeric;
    v_at timestamptz;
    v_balance numeric;
BEGIN
    FOR v_card IN SELECT c.*, p.card_at FROM member_cards c JOIN seed_tmp.people p ON p.card_id = c.id
                  WHERE p.kind = 'bulk' AND p.card_at < now() - interval '3 days' LOOP
        IF random() < 0.5 AND v_card.status = 'ACTIVE' THEN
            v_amount := seed_tmp.pick(ARRAY['20', '50', '100'])::numeric;
            v_at := v_card.card_at + random() * (now() - interval '1 day' - v_card.card_at);
            INSERT INTO member_card_top_up_requests (card_id, amount, note, receipt_url, receipt_name, status, reviewed_by, reviewed_at, created_at)
            VALUES (v_card.id, v_amount, 'ABA transfer', 'storage://private/receipts/' || gen_random_uuid() || '.png', 'aba-transfer.png',
                    'APPROVED', v_admin, v_at + interval '3 hours', v_at)
            RETURNING id INTO v_req;
            PERFORM seed_tmp.card_tx(v_card.id, 'TOP_UP', v_amount, 'TOPUP-' || v_req, 'Top-up request approved', v_admin, v_at + interval '3 hours');
        END IF;
        IF random() < 0.15 THEN
            v_at := v_card.card_at + random() * (now() - interval '1 day' - v_card.card_at);
            INSERT INTO member_card_top_up_requests (card_id, amount, note, receipt_url, receipt_name, status, review_note, reviewed_by, reviewed_at, created_at)
            VALUES (v_card.id, 30, 'Transfer screenshot attached', 'storage://private/receipts/' || gen_random_uuid() || '.jpg', 'screenshot.jpg',
                    'REJECTED', 'The transfer has not reached our account.', v_admin, v_at + interval '5 hours', v_at);
        END IF;
        IF random() < 0.2 AND v_card.status = 'ACTIVE' THEN
            INSERT INTO member_card_top_up_requests (card_id, amount, note, status, created_at)
            VALUES (v_card.id, seed_tmp.pick(ARRAY['25', '40', '75'])::numeric, 'Wing transfer this morning', 'PENDING',
                    now() - make_interval(hours => seed_tmp.rint(1, 40)));
        END IF;
    END LOOP;

    -- Admin corrections: a cash top-up and a reversal of a duplicate one
    FOR v_card IN SELECT c.* FROM member_cards c JOIN seed_tmp.people p ON p.card_id = c.id
                  WHERE p.kind = 'bulk' AND p.card_at < now() - interval '10 days' ORDER BY random() LIMIT 6 LOOP
        PERFORM seed_tmp.card_tx(v_card.id, 'TOP_UP', 20, 'ADJUST-' || v_card.id || '-1', 'Cash top-up at the front desk', v_admin, now() - interval '9 days');
        PERFORM seed_tmp.card_tx(v_card.id, 'DEDUCTION', 20, 'ADJUST-' || v_card.id || '-2', 'Duplicate top-up reversed', v_admin, now() - interval '8 days');
    END LOOP;
END $seed$;

UPDATE member_card_transactions t SET balance_after = x.balance
FROM (SELECT id, sum(CASE WHEN type IN ('TOP_UP', 'REFUND') THEN amount ELSE -amount END)
                 OVER (PARTITION BY card_id ORDER BY created_at, id) AS balance
      FROM member_card_transactions) x
WHERE x.id = t.id;
UPDATE member_cards c
SET balance = coalesce((SELECT t.balance_after FROM member_card_transactions t WHERE t.card_id = c.id ORDER BY t.created_at DESC, t.id DESC LIMIT 1), 0),
    updated_at = greatest(c.updated_at, coalesce((SELECT max(created_at) FROM member_card_transactions t WHERE t.card_id = c.id), c.updated_at));

-- ===============================================================================================================
-- 12. Instructor earnings and payouts
-- ===============================================================================================================
INSERT INTO instructor_earnings (instructor_id, order_id, course_id, type, gross, platform_fee, net, share_percent, currency, status, available_at, created_at)
SELECT co.owner_id, o.id, o.course_id, 'SALE', o.amount,
       o.amount - round(o.amount * coalesce(rs.instructor_percent, 70) / 100, 2),
       round(o.amount * coalesce(rs.instructor_percent, 70) / 100, 2),
       coalesce(rs.instructor_percent, 70), o.currency,
       CASE WHEN o.status = 'REFUNDED' AND o.refunded_at < o.paid_at + interval '14 days' THEN 'REVERSED'
            WHEN o.paid_at + interval '14 days' <= now() THEN 'AVAILABLE' ELSE 'PENDING' END,
       o.paid_at + interval '14 days', o.paid_at
FROM orders o
JOIN courses co ON co.id = o.course_id
LEFT JOIN course_revenue_shares rs ON rs.course_id = co.id
WHERE co.owner_id IS NOT NULL AND o.status IN ('PAID', 'REFUNDED') AND o.amount > 0 AND o.paid_at IS NOT NULL;

-- Refunds after the money was released come off the next payout
INSERT INTO instructor_earnings (instructor_id, order_id, course_id, type, gross, platform_fee, net, share_percent, currency, status, available_at, created_at)
SELECT e.instructor_id, e.order_id, e.course_id, 'REFUND', -e.gross, -e.platform_fee, -e.net, e.share_percent, e.currency,
       'AVAILABLE', o.refunded_at, o.refunded_at
FROM instructor_earnings e JOIN orders o ON o.id = e.order_id
WHERE e.type = 'SALE' AND o.status = 'REFUNDED' AND e.status <> 'REVERSED';

DO $seed$
DECLARE
    v_admin uuid := seed_tmp.admin_id();
    v_instructor record;
    v_month date;
    v_total numeric;
    v_payout int;
    v_at timestamptz;
    v_methods text[] := ARRAY['ABA Bank', 'ACLEDA Bank', 'Wing', 'Bank transfer', 'PayPal'];
    v_method text;
    v_idx int := 0;
BEGIN
    FOR v_instructor IN SELECT u.id, u.first_name || ' ' || u.last_name AS name FROM users u WHERE u.role = 'ROLE_INSTRUCTOR' ORDER BY u.email LOOP
        v_idx := v_idx + 1;
        v_method := v_methods[1 + v_idx % 5];
        -- Monthly payouts for everything released before this month
        FOR v_month IN
            SELECT DISTINCT date_trunc('month', available_at)::date FROM instructor_earnings
            WHERE instructor_id = v_instructor.id AND status = 'AVAILABLE' AND available_at < date_trunc('month', now()) - interval '1 month'
            ORDER BY 1
        LOOP
            SELECT coalesce(sum(net), 0) INTO v_total FROM instructor_earnings
            WHERE instructor_id = v_instructor.id AND status = 'AVAILABLE' AND payout_id IS NULL AND available_at < v_month + interval '1 month';
            CONTINUE WHEN v_total < 10;
            v_at := v_month + interval '1 month' + make_interval(days => seed_tmp.rint(1, 5), hours => seed_tmp.rint(8, 18));
            INSERT INTO payout_requests (instructor_id, amount, currency, method, account_details, note, status, decision_note,
                                         payment_reference, reviewed_by, reviewed_at, paid_at, created_at)
            VALUES (v_instructor.id, v_total, 'USD', v_method, v_method || ' account 000 ' || seed_tmp.rint(100, 999) || ' ' || seed_tmp.rint(100, 999)
                    || ' (' || v_instructor.name || ')', 'Monthly payout', 'PAID', NULL, 'TRX-' || upper(seed_tmp.hex(10)), v_admin,
                    v_at + interval '1 day', v_at + interval '2 days', v_at)
            RETURNING id INTO v_payout;
            UPDATE instructor_earnings SET status = 'PAID', payout_id = v_payout
            WHERE instructor_id = v_instructor.id AND status = 'AVAILABLE' AND payout_id IS NULL AND available_at < v_month + interval '1 month';
        END LOOP;

        -- A rejected request from the past (its rows were released again)
        IF v_idx % 3 = 0 THEN
            INSERT INTO payout_requests (instructor_id, amount, currency, method, account_details, status, decision_note, reviewed_by, reviewed_at, created_at)
            VALUES (v_instructor.id, 50, 'USD', 'PayPal', 'paypal: ' || lower(replace(v_instructor.name, ' ', '.')) || '@example.com', 'REJECTED',
                    'PayPal payouts are not available in your country. Please choose a bank account.', v_admin,
                    now() - interval '58 days', now() - interval '60 days');
        END IF;

        -- The open request claims everything available now
        SELECT coalesce(sum(net), 0) INTO v_total FROM instructor_earnings
        WHERE instructor_id = v_instructor.id AND status = 'AVAILABLE' AND payout_id IS NULL;
        IF v_total >= 10 AND v_idx % 4 <> 0 THEN
            INSERT INTO payout_requests (instructor_id, amount, currency, method, account_details, note, status, reviewed_by, reviewed_at, created_at)
            VALUES (v_instructor.id, v_total, 'USD', v_method, v_method || ' account 000 ' || seed_tmp.rint(100, 999) || ' ' || seed_tmp.rint(100, 999)
                    || ' (' || v_instructor.name || ')', 'Please pay before the end of the month, thank you!',
                    CASE WHEN v_idx % 4 = 1 THEN 'APPROVED' ELSE 'REQUESTED' END,
                    CASE WHEN v_idx % 4 = 1 THEN v_admin END, CASE WHEN v_idx % 4 = 1 THEN now() - interval '1 day' END,
                    now() - make_interval(days => seed_tmp.rint(1, 4)))
            RETURNING id INTO v_payout;
            UPDATE instructor_earnings SET payout_id = v_payout
            WHERE instructor_id = v_instructor.id AND status = 'AVAILABLE' AND payout_id IS NULL;
        END IF;
    END LOOP;
END $seed$;

-- ===============================================================================================================
-- 13. Community: Q&A, notes, watch positions, review replies, votes, reports, quiz grants
-- ===============================================================================================================
DO $seed$
DECLARE
    v_row record;
    v_question int;
    v_staff uuid;
    v_staff_name text;
    v_questions text[] := ARRAY[
        'Could you explain the difference between the two approaches shown around the middle of the video?',
        'I get an error when I follow the steps exactly. Did something change in the latest version?',
        'Is there a recommended way to practise this outside the course?',
        'Why do we do it this way instead of the simpler option?',
        'Can you share the slides or the source code for this lesson?',
        'How would this work in a bigger project with a team?',
        'Is this still the best practice in 2026?',
        'I am stuck on the exercise. Any hint without giving away the answer?'];
    v_staff_answers text[] := ARRAY[
        'Great question! The first approach is simpler, the second scales better. Start simple and switch when you feel the pain.',
        'Yes, a recent release renamed that option. I added a note under the video with the new name.',
        'Try rebuilding the example from memory, then change one requirement and see what breaks.',
        'The source code is in the resources of the next lesson. Have fun!',
        'Hint: print the intermediate values and compare them with the example output.'];
    v_peer_answers text[] := ARRAY['Same problem here, restarting the dev server fixed it for me.',
        'Thanks for asking, I was wondering the same thing!', 'This blog post helped me a lot with the same question.',
        'I solved it by reading the error message carefully, it tells you the missing import.'];
    v_notes text[] := ARRAY['Key idea: start simple, then refactor once it works.', 'Review this part again before the quiz.',
        'Try this on the project at work.', 'The shortcut at this timestamp saves a lot of time.',
        'Write down the three steps from the slide.', 'Question for Q&A: how does this scale?',
        'Great example, reuse it in my portfolio project.', 'Remember to check the edge cases.'];
BEGIN
    -- Questions from learners on lessons they finished, answered by staff and peers
    FOR v_row IN
        SELECT lp.student_id, lp.lesson_id, lp.completed_at, st.user_id, st.name, co.owner_id, co.instructor
        FROM lesson_progress lp
        JOIN students st ON st.id = lp.student_id AND st.user_id IS NOT NULL
        JOIN lessons l ON l.id = lp.lesson_id AND l.type IN ('VIDEO', 'ARTICLE', 'CODE')
        JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id
        WHERE random() < 0.05
        LIMIT 320
    LOOP
        INSERT INTO lesson_questions (lesson_id, author_id, author_name, body, resolved, created_at, hidden)
        VALUES (v_row.lesson_id, v_row.user_id, v_row.name, seed_tmp.pick(v_questions), false,
                v_row.completed_at - make_interval(mins => seed_tmp.rint(5, 600)), random() < 0.02)
        RETURNING id INTO v_question;
        v_staff := coalesce(v_row.owner_id, seed_tmp.admin_id());
        SELECT first_name || ' ' || last_name INTO v_staff_name FROM users WHERE id = v_staff;
        IF random() < 0.75 AND v_row.completed_at < now() - interval '1 hour' THEN
            INSERT INTO lesson_answers (question_id, author_id, author_name, from_staff, body, created_at)
            VALUES (v_question, v_staff, v_staff_name, true, seed_tmp.pick(v_staff_answers),
                    least(v_row.completed_at + make_interval(hours => seed_tmp.rint(1, 40)), now() - interval '10 minutes'));
            UPDATE lesson_questions SET resolved = random() < 0.85 WHERE id = v_question;
        END IF;
        IF random() < 0.35 THEN
            INSERT INTO lesson_answers (question_id, author_id, author_name, from_staff, body, created_at, hidden)
            SELECT v_question, p.user_id, p.name, false, seed_tmp.pick(v_peer_answers),
                   least(v_row.completed_at + make_interval(hours => seed_tmp.rint(2, 60)), now() - interval '5 minutes'), random() < 0.04
            FROM seed_tmp.people p WHERE p.kind = 'bulk' AND p.user_id <> v_row.user_id ORDER BY random() LIMIT 1;
        END IF;
    END LOOP;
    -- Vannak's answered question and Rithy's open one
    INSERT INTO lesson_questions (lesson_id, author_id, author_name, body, resolved, created_at)
    SELECT l.id, seed_tmp.uid('vannak.kim@demo.courseaurahub.io'), 'Vannak Kim',
           'My CSV uses semicolons and read_csv puts everything in one column. What am I missing?', true, now() - interval '56 days'
    FROM lessons l JOIN course_sections s ON s.id = l.section_id
    WHERE s.course_id = seed_tmp.cid('python-for-data-analysis') AND s.position = 1 AND l.position = 0
    RETURNING id INTO v_question;
    INSERT INTO lesson_answers (question_id, author_id, author_name, from_staff, body, created_at)
    VALUES (v_question, seed_tmp.uid('sokha.chan@demo.courseaurahub.io'), 'Sokha Chan', true,
            'Pass sep='';'' to read_csv. Files exported from Excel in many regions use semicolons.', now() - interval '55 days'),
           (v_question, seed_tmp.uid('vannak.kim@demo.courseaurahub.io'), 'Vannak Kim', false, 'That fixed it, thank you!', now() - interval '55 days' + interval '1 hour');
    INSERT INTO lesson_questions (lesson_id, author_id, author_name, body, resolved, created_at)
    SELECT l.id, seed_tmp.uid('rithy.pov@demo.courseaurahub.io'), 'Rithy Pov',
           'When should I fill missing values instead of dropping the rows?', false, now() - interval '6 hours'
    FROM lessons l JOIN course_sections s ON s.id = l.section_id
    WHERE s.course_id = seed_tmp.cid('python-for-data-analysis') AND s.position = 1 AND l.position = 1;

    -- Private notes and video bookmarks
    INSERT INTO lesson_notes (student_id, lesson_id, body, position_seconds, created_at, updated_at)
    SELECT lp.student_id, lp.lesson_id,
           CASE WHEN l.type = 'ARTICLE' OR random() < 0.85 THEN seed_tmp.pick(v_notes) END,
           CASE WHEN l.type = 'VIDEO' THEN seed_tmp.rint(10, greatest(coalesce(l.duration_seconds, 600) - 10, 11)) END,
           lp.completed_at - interval '10 minutes', lp.completed_at - interval '10 minutes' + make_interval(days => seed_tmp.rint(0, 3))
    FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id
    WHERE l.type IN ('VIDEO', 'ARTICLE') AND random() < 0.12;
    UPDATE lesson_notes SET updated_at = least(updated_at, now()) WHERE updated_at > now();

    -- Where each learner stopped in the next video they haven't finished
    INSERT INTO lesson_watch_positions (student_id, lesson_id, position_seconds, updated_at)
    SELECT DISTINCT ON (sc.student_id, sc.course_id) sc.student_id, l.id,
           seed_tmp.rint(30, greatest(coalesce(l.duration_seconds, 600) - 30, 31)),
           now() - make_interval(days => seed_tmp.rint(0, 30), hours => seed_tmp.rint(0, 23))
    FROM student_courses sc
    JOIN course_sections s ON s.course_id = sc.course_id
    JOIN lessons l ON l.section_id = s.id AND l.type = 'VIDEO'
    WHERE NOT sc.completed
      AND NOT EXISTS (SELECT 1 FROM lesson_progress lp WHERE lp.student_id = sc.student_id AND lp.lesson_id = l.id)
    ORDER BY sc.student_id, sc.course_id, s.position, l.position;
    -- And positions saved on videos they finished
    INSERT INTO lesson_watch_positions (student_id, lesson_id, position_seconds, updated_at)
    SELECT lp.student_id, lp.lesson_id, greatest(coalesce(l.duration_seconds, 600) - seed_tmp.rint(1, 20), 0), lp.completed_at
    FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id
    WHERE l.type = 'VIDEO' AND random() < 0.3
    ON CONFLICT DO NOTHING;

    -- Instructor replies to reviews
    UPDATE course_reviews r
    SET reply = CASE WHEN r.rating >= 4 THEN seed_tmp.pick(ARRAY['Thank you so much! Glad the course helped.',
                         'Thanks for the kind words. Share your project in the Q&A, I would love to see it!',
                         'Happy to hear that. More advanced lessons are coming soon.'])
                     ELSE seed_tmp.pick(ARRAY['Thanks for the honest feedback. I am re-recording section two this month.',
                         'Sorry it did not meet your expectations. Message me in the Q&A and I will help you get unstuck.']) END,
        reply_author_id = coalesce(co.owner_id, seed_tmp.admin_id()),
        reply_author_name = coalesce((SELECT first_name || ' ' || last_name FROM users WHERE id = co.owner_id), 'Dara Sok'),
        replied_at = least(r.created_at + make_interval(hours => seed_tmp.rint(3, 96)), now() - interval '5 minutes')
    FROM courses co WHERE co.id = r.course_id AND random() < 0.35;

    -- Helpful votes from other learners of the same course
    INSERT INTO review_votes (review_id, student_id, created_at)
    SELECT r.id, sc.student_id, least(r.created_at + make_interval(days => seed_tmp.rint(1, 30)), now() - interval '1 minute')
    FROM course_reviews r JOIN student_courses sc ON sc.course_id = r.course_id AND sc.student_id <> r.student_id
    WHERE random() < 0.18;

    -- Reports: open, hidden (content hidden) and dismissed
    INSERT INTO content_reports (target_type, target_id, reporter_id, reason, status, resolved_by, resolved_at, created_at)
    SELECT 'REVIEW', r.id, p.user_id, seed_tmp.pick(ARRAY['Spam or advertising', 'Offensive language', 'Not about this course']),
           x.status, CASE WHEN x.status <> 'OPEN' THEN seed_tmp.admin_id() END,
           CASE WHEN x.status <> 'OPEN' THEN r.created_at + interval '2 days' END, r.created_at + interval '1 day'
    FROM (SELECT r.*, row_number() OVER (ORDER BY random()) AS rn FROM course_reviews r WHERE r.rating <= 2 AND r.created_at < now() - interval '3 days') r
    CROSS JOIN LATERAL (SELECT (ARRAY['OPEN', 'HIDDEN', 'DISMISSED', 'OPEN', 'DISMISSED', 'HIDDEN'])[1 + r.rn % 6] AS status) x
    CROSS JOIN LATERAL (SELECT user_id FROM seed_tmp.people WHERE kind = 'bulk' ORDER BY random() LIMIT 1) p
    WHERE r.rn <= 10;
    INSERT INTO content_reports (target_type, target_id, reporter_id, reason, status, resolved_by, resolved_at, created_at)
    SELECT 'QUESTION', q.id, p.user_id, 'Off-topic', CASE WHEN q.hidden THEN 'HIDDEN' ELSE 'OPEN' END,
           CASE WHEN q.hidden THEN seed_tmp.admin_id() END, CASE WHEN q.hidden THEN q.created_at + interval '1 day' END, q.created_at + interval '2 hours'
    FROM (SELECT * FROM lesson_questions WHERE hidden OR random() < 0.02 LIMIT 8) q
    CROSS JOIN LATERAL (SELECT user_id FROM seed_tmp.people WHERE kind = 'bulk' AND user_id IS DISTINCT FROM q.author_id ORDER BY random() LIMIT 1) p;
    INSERT INTO content_reports (target_type, target_id, reporter_id, reason, status, resolved_by, resolved_at, created_at)
    SELECT 'ANSWER', a.id, p.user_id, 'Wrong or misleading answer', CASE WHEN a.hidden THEN 'HIDDEN' ELSE 'DISMISSED' END,
           seed_tmp.admin_id(), a.created_at + interval '1 day', a.created_at + interval '3 hours'
    FROM (SELECT * FROM lesson_answers WHERE NOT from_staff AND created_at < now() - interval '2 days' AND (hidden OR random() < 0.05) LIMIT 6) a
    CROSS JOIN LATERAL (SELECT user_id FROM seed_tmp.people WHERE kind = 'bulk' AND user_id IS DISTINCT FROM a.author_id ORDER BY random() LIMIT 1) p;
    UPDATE course_reviews r SET hidden = true
    WHERE EXISTS (SELECT 1 FROM content_reports cr WHERE cr.target_type = 'REVIEW' AND cr.target_id = r.id AND cr.status = 'HIDDEN');

    -- Learners who used up their quiz attempts; some were given more
    INSERT INTO quiz_attempts (student_id, lesson_id, correct_count, total_count, score_percent, passed, started_at, submitted_at)
    SELECT x.student_id, x.lesson_id, g.c, 4, g.c * 25, false,
           now() - make_interval(days => 10 - g.n) - interval '12 minutes', now() - make_interval(days => 10 - g.n)
    FROM (SELECT sc.student_id, l.id AS lesson_id FROM student_courses sc
          JOIN course_sections s ON s.course_id = sc.course_id JOIN lessons l ON l.section_id = s.id
          WHERE l.type = 'QUIZ' AND l.quiz_max_attempts = 3 AND NOT sc.completed
            AND NOT EXISTS (SELECT 1 FROM quiz_attempts qa WHERE qa.student_id = sc.student_id AND qa.lesson_id = l.id)
            AND sc.student_id IN (SELECT student_id FROM seed_tmp.people WHERE kind = 'bulk')
          ORDER BY random() LIMIT 8) x,
         (VALUES (1, 1), (2, 1), (3, 2)) AS g(n, c);
    INSERT INTO quiz_attempt_grants (student_id, lesson_id, extra_attempts, granted_by, updated_at)
    SELECT qa.student_id, qa.lesson_id, seed_tmp.rint(1, 2), coalesce(co.owner_id, seed_tmp.admin_id()), now() - interval '2 days'
    FROM (SELECT student_id, lesson_id FROM quiz_attempts GROUP BY student_id, lesson_id
          HAVING count(*) FILTER (WHERE NOT passed) >= 3 AND NOT bool_or(passed)) qa
    JOIN lessons l ON l.id = qa.lesson_id JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id
    WHERE random() < 0.6
    ON CONFLICT DO NOTHING;

    -- A timed quiz someone is taking right now
    INSERT INTO quiz_sessions (student_id, lesson_id, question_ids, started_at, expires_at)
    SELECT sc.student_id, l.id, (SELECT string_agg(id::text, ',') FROM (SELECT id FROM quiz_questions WHERE lesson_id = l.id LIMIT 4) q),
           now() - interval '4 minutes', now() + interval '11 minutes'
    FROM student_courses sc JOIN course_sections s ON s.course_id = sc.course_id JOIN lessons l ON l.section_id = s.id
    WHERE l.type = 'QUIZ' AND l.quiz_time_limit_minutes IS NOT NULL AND NOT sc.completed
      AND NOT EXISTS (SELECT 1 FROM quiz_attempts qa WHERE qa.student_id = sc.student_id AND qa.lesson_id = l.id)
    LIMIT 3;
END $seed$;

-- ===============================================================================================================
-- 14. Live classes, attendance, calendar feeds
-- ===============================================================================================================
DO $seed$
DECLARE
    v_course record;
    v_session int;
    v_s record;
BEGIN
    FOR v_course IN
        SELECT co.* FROM courses co WHERE co.status = 'PUBLISHED' AND co.owner_id IS NOT NULL
        ORDER BY (SELECT count(*) FROM student_courses sc WHERE sc.course_id = co.id) DESC LIMIT 9
    LOOP
        FOR v_s IN SELECT * FROM (VALUES
            ('Kick-off Q&A', -40, 60, false), ('Project clinic', -19, 90, false), ('Office hours', -5, 45, false),
            ('Live coding session', 1, 60, false), ('Exam prep Q&A', 9, 60, false), ('Guest talk (moved)', 3, 45, true),
            ('Weekly office hours', 0, 45, false)
        ) AS x(title, day_offset, minutes, cancelled)
        LOOP
            INSERT INTO live_sessions (course_id, title, description, starts_at, duration_minutes, meeting_url, recording_lesson_id,
                                       cancelled, reminded_hour, reminded_soon, created_by, created_at, updated_at)
            VALUES (v_course.id, v_s.title || ': ' || v_course.title,
                    'Bring your questions about ' || v_course.title || '. We will look at learner projects and answer questions live.',
                    CASE WHEN v_s.day_offset = 0 THEN now() + interval '50 minutes'
                         ELSE date_trunc('hour', now()) + make_interval(days => v_s.day_offset, hours => seed_tmp.rint(1, 6)) END,
                    v_s.minutes, 'https://meet.google.com/' || lower(seed_tmp.code('', 1, 3)) || '-' || lower(seed_tmp.code('', 1, 4)) || '-' || lower(seed_tmp.code('', 1, 3)),
                    CASE WHEN v_s.day_offset < -10 THEN (SELECT l.id FROM lessons l JOIN course_sections s ON s.id = l.section_id
                                                         WHERE s.course_id = v_course.id AND l.type = 'VIDEO' ORDER BY random() LIMIT 1) END,
                    v_s.cancelled, v_s.day_offset <= 0, v_s.day_offset < 0, v_course.owner_id,
                    now() + make_interval(days => least(v_s.day_offset, 0) - 14), now() + make_interval(days => least(v_s.day_offset, 0) - 3))
            RETURNING id INTO v_session;
            IF v_s.day_offset < 0 THEN
                INSERT INTO live_attendance (session_id, student_id, joined_at)
                SELECT v_session, e.student_id, now() + make_interval(days => v_s.day_offset, mins => seed_tmp.rint(-5, 20))
                FROM enrollments e WHERE e.course_id = v_course.id AND e.enrolled_at < (now() + make_interval(days => v_s.day_offset))::date
                  AND random() < 0.45;
            END IF;
        END LOOP;
    END LOOP;

    INSERT INTO calendar_tokens (user_id, token_hash, created_at)
    SELECT p.user_id, 'seed:' || CASE WHEN p.email = 'vannak.kim@demo.courseaurahub.io' THEN 'vannak-calendar-demo-token-2026' ELSE seed_tmp.hex(40) END,
           now() - make_interval(days => seed_tmp.rint(1, 60))
    FROM seed_tmp.people p
    WHERE p.student_id IS NOT NULL AND (p.email = 'vannak.kim@demo.courseaurahub.io' OR (p.kind = 'bulk' AND random() < 0.15));
END $seed$;

-- ===============================================================================================================
-- 15. Course surveys, prompts and responses
-- ===============================================================================================================
INSERT INTO course_surveys (course_id, trigger, questions, active, created_at, updated_at)
SELECT co.id, CASE WHEN row_number() OVER (ORDER BY co.id) % 2 = 0 THEN 'COMPLETION' ELSE 'PERCENT_50' END,
       $j$[{"id": "q1", "text": "How clear were the lessons?", "type": "rating", "options": [], "required": true},
           {"id": "q2", "text": "What did you like most?", "type": "choice", "options": ["Projects", "Videos", "Quizzes", "Instructor support"], "required": false},
           {"id": "q3", "text": "What should we improve?", "type": "text", "options": [], "required": false}]$j$::jsonb,
       co.slug <> 'react-19-complete-guide', co.created_at + interval '10 days', co.created_at + interval '10 days'
FROM courses co WHERE co.status = 'PUBLISHED' AND co.owner_id IS NOT NULL
ORDER BY (SELECT count(*) FROM student_courses sc WHERE sc.course_id = co.id) DESC LIMIT 12;

-- Learners are asked once: at the lesson that takes them to half way, or the one that completes the course
WITH totals AS (
    SELECT s.course_id, count(*) AS total FROM lessons l JOIN course_sections s ON s.id = l.section_id GROUP BY s.course_id
), progress AS (
    SELECT lp.student_id, s.course_id, lp.completed_at,
           row_number() OVER (PARTITION BY lp.student_id, s.course_id ORDER BY lp.completed_at, lp.lesson_id) AS rn
    FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id JOIN course_sections s ON s.id = l.section_id
)
INSERT INTO survey_prompts (survey_id, student_id, prompted_at)
SELECT sv.id, p.student_id, p.completed_at + interval '1 minute'
FROM course_surveys sv
JOIN totals t ON t.course_id = sv.course_id
JOIN progress p ON p.course_id = sv.course_id
               AND p.rn = CASE WHEN sv.trigger = 'COMPLETION' THEN t.total ELSE ceil(t.total / 2.0) END
JOIN student_courses sc ON sc.student_id = p.student_id AND sc.course_id = sv.course_id
WHERE sv.active;

INSERT INTO survey_responses (survey_id, student_id, nps_score, answers, created_at)
SELECT sp.survey_id, sp.student_id, x.nps,
       jsonb_strip_nulls(jsonb_build_object(
           'q1', greatest(1, least(5, x.nps / 2 + seed_tmp.rint(0, 1))),
           'q2', CASE WHEN random() < 0.8 THEN seed_tmp.pick(ARRAY['Projects', 'Videos', 'Quizzes', 'Instructor support']) END,
           'q3', CASE WHEN random() < 0.5 THEN seed_tmp.pick(ARRAY['More exercises please.', 'Shorter videos.',
                     'A downloadable cheat sheet would help.', 'Nothing, it was great!', 'Subtitles in Khmer.']) END)),
       least(sp.prompted_at + make_interval(mins => seed_tmp.rint(2, 2000)), now() - interval '1 minute')
FROM survey_prompts sp
CROSS JOIN LATERAL (SELECT (ARRAY[10, 10, 10, 9, 9, 9, 9, 8, 8, 7, 7, 6, 5, 3])[seed_tmp.rint(1, 14) + 0 * sp.student_id] AS nps) x
WHERE random() < 0.65;

-- ===============================================================================================================
-- 16. Learning paths and path certificates
-- ===============================================================================================================
INSERT INTO learning_paths (slug, title, description, thumbnail, status, created_at, updated_at) VALUES
    ('frontend-developer', 'Frontend Developer', 'Version control, JavaScript, TypeScript and Nuxt, in the order teams use them. Finish all four for the path certificate.',
     'https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=800&h=450&fit=crop', 'PUBLISHED', now() - interval '400 days', now() - interval '30 days'),
    ('cloud-native-backend', 'Cloud-Native Backend Engineer', 'Build Java services, ship them in containers and run them on Kubernetes with Terraform.',
     'https://images.unsplash.com/photo-1558494949-ef010cbdcc31?w=800&h=450&fit=crop', 'PUBLISHED', now() - interval '300 days', now() - interval '40 days'),
    ('data-analyst-track', 'Data Analyst Track', 'SQL, Python and visualization for everyday analysis.',
     'https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800&h=450&fit=crop', 'PUBLISHED', now() - interval '250 days', now() - interval '20 days'),
    ('ai-engineer', 'AI Engineer', 'From Python to machine learning to production AI agents.',
     'https://images.unsplash.com/photo-1677442136019-21780ecad995?w=800&h=450&fit=crop', 'PUBLISHED', now() - interval '180 days', now() - interval '15 days'),
    ('mobile-developer', 'Mobile Developer', 'Flutter, Kotlin and SwiftUI. Coming soon.', NULL, 'DRAFT', now() - interval '10 days', now() - interval '10 days');
INSERT INTO learning_path_courses (path_id, course_id, position)
SELECT p.id, seed_tmp.cid(x.course), x.position
FROM (VALUES ('frontend-developer', 'git-github-fundamentals', 0), ('frontend-developer', 'modern-javascript-from-zero', 1),
             ('frontend-developer', 'typescript-for-vue-devs', 2), ('frontend-developer', 'nuxt-3-vue-enterprise', 3),
             ('cloud-native-backend', 'java-21-fundamentals', 0), ('cloud-native-backend', 'spring-boot-microservices', 1),
             ('cloud-native-backend', 'docker-kubernetes-devops', 2), ('cloud-native-backend', 'terraform-infrastructure-as-code', 3),
             ('data-analyst-track', 'sql-for-analysts', 0), ('data-analyst-track', 'python-for-data-analysis', 1),
             ('data-analyst-track', 'data-visualization-storytelling', 2),
             ('ai-engineer', 'python-for-data-analysis', 0), ('ai-engineer', 'machine-learning-foundations', 1), ('ai-engineer', 'llm-agents-engineering', 2),
             ('mobile-developer', 'flutter-crossplatform-mobile', 0), ('mobile-developer', 'kotlin-android-development', 1),
             ('mobile-developer', 'swiftui-ios-apps', 2)) AS x(slug, course, position)
JOIN learning_paths p ON p.slug = x.slug;

INSERT INTO learning_path_certificates (certificate_number, path_id, student_id, learner_name, path_title, issued_at)
SELECT seed_tmp.code('CAH', 4, 4), p.id, st.id, st.name, p.title,
       (SELECT max(cc.issued_at) FROM course_certificates cc JOIN learning_path_courses lpc ON lpc.course_id = cc.course_id
        WHERE lpc.path_id = p.id AND cc.student_id = st.id)
FROM learning_paths p CROSS JOIN students st
WHERE p.status = 'PUBLISHED'
  AND NOT EXISTS (SELECT 1 FROM learning_path_courses lpc WHERE lpc.path_id = p.id
                  AND NOT EXISTS (SELECT 1 FROM course_certificates cc WHERE cc.course_id = lpc.course_id AND cc.student_id = st.id));

-- ===============================================================================================================
-- 17. Announcements and course revisions
-- ===============================================================================================================
INSERT INTO course_announcements (course_id, author_id, author_name, title, body, created_at)
SELECT co.id, co.owner_id, co.instructor, a.title, a.body, now() - make_interval(days => a.days + seed_tmp.rint(0, 5))
FROM courses co
CROSS JOIN (VALUES
    ('Welcome to the course!', 'Thanks for joining. Start with the first section and post your questions in the Q&A, I read every one.', 120),
    ('New practice material', 'I added a new exercise and extra reading to the second section so you can practise on fresh examples.', 35),
    ('Live session this week', 'Join me for a live Q&A this Friday at 7pm Phnom Penh time. The link is on the course page.', 4)
) AS a(title, body, days)
WHERE co.status = 'PUBLISHED' AND co.owner_id IS NOT NULL AND (a.days > 100 OR random() < 0.7);

INSERT INTO course_revisions (course_id, submitted_by, changes, previous, summary, status, review_note, reviewed_by, reviewed_at, created_at, updated_at)
SELECT co.id, co.owner_id,
       jsonb_build_object('title', co.title || x.suffix, 'price', co.price + x.price_delta)::text,
       jsonb_build_object('title', co.title, 'price', co.price)::text,
       x.summary, x.status, x.note,
       CASE WHEN x.status IN ('APPROVED', 'REJECTED') THEN seed_tmp.admin_id() END,
       CASE WHEN x.status IN ('APPROVED', 'REJECTED') THEN now() - make_interval(days => x.days - 1) END,
       now() - make_interval(days => x.days), now() - make_interval(days => greatest(x.days - 1, 0))
FROM (VALUES ('nuxt-3-vue-enterprise', ' (2026 Edition)', 10, 'Updated for Nuxt 4 and a new title', 'PENDING', NULL, 2),
             ('docker-kubernetes-devops', ': From Zero to Production', 20, 'New title and higher price after adding Helm', 'REJECTED',
              'The price change is too large for existing learners. Please keep the current price.', 25),
             ('react-19-complete-guide', ' with Next.js', 0, 'Add Next.js to the title', 'WITHDRAWN', NULL, 18),
             ('sql-for-analysts', '', -5, 'Lower price for the launch of the data path', 'PENDING', NULL, 1),
             ('machine-learning-foundations', '', 0, 'Clearer title', 'APPROVED', NULL, 60)) AS x(slug, suffix, price_delta, summary, status, note, days)
JOIN courses co ON co.slug = x.slug;
UPDATE course_revisions SET changes = jsonb_build_object('description', 'Regression, classification and model evaluation with scikit-learn, with small real datasets and a final project.')::text,
                            previous = jsonb_build_object('description', 'Regression, classification and model evaluation with scikit-learn, explained with small real datasets.')::text
WHERE status = 'APPROVED';

-- ===============================================================================================================
-- 18. Assistant chats, captions, uploaded files, transcode history, reminders
-- ===============================================================================================================
DO $seed$
DECLARE
    v_row record;
    v_conv bigint;
    v_at timestamptz;
    v_questions text[] := ARRAY['Can you explain this lesson in simpler words?', 'What are the three key ideas here?',
        'Give me an example I can try at work.', 'What is the most common mistake beginners make with this?',
        'How does this connect to the previous section?'];
    v_answers text[] := ARRAY['Sure! In short: the lesson starts from the problem, shows one technique that solves it and finishes with a checklist. Think of it as learning the "why" before the "how".',
        E'The three key ideas are:\n1. Understand the problem first.\n2. Apply the technique in small steps.\n3. Check for the common mistakes at the end.',
        'Try taking a small task from your work this week and apply the checklist from the end of the lesson to it.',
        'The most common mistake is skipping the small worked example and jumping straight to a big project.'];
BEGIN
    FOR v_row IN
        SELECT DISTINCT ON (lp.student_id, a.lesson_id) lp.student_id, a.lesson_id, lp.completed_at
        FROM lesson_ai_sources a JOIN lesson_progress lp ON lp.lesson_id = a.lesson_id
        WHERE random() < 0.15 LIMIT 70
    LOOP
        v_at := v_row.completed_at - interval '20 minutes';
        INSERT INTO assistant_conversations (student_id, lesson_id, created_at) VALUES (v_row.student_id, v_row.lesson_id, v_at) RETURNING id INTO v_conv;
        FOR k IN 1..seed_tmp.rint(1, 3) LOOP
            INSERT INTO assistant_messages (conversation_id, role, content, input_tokens, output_tokens, created_at)
            VALUES (v_conv, 'USER', seed_tmp.pick(v_questions), NULL, NULL, v_at + make_interval(mins => k * 3)),
                   (v_conv, 'ASSISTANT', seed_tmp.pick(v_answers), seed_tmp.rint(1800, 2600), seed_tmp.rint(90, 400),
                    v_at + make_interval(mins => k * 3, secs => 8));
        END LOOP;
    END LOOP;
    INSERT INTO assistant_usage (student_id, usage_date, requests, tokens)
    SELECT c.student_id, m.created_at::date, count(*), sum(coalesce(m.input_tokens, 0) + coalesce(m.output_tokens, 0))
    FROM assistant_messages m JOIN assistant_conversations c ON c.id = m.conversation_id
    WHERE m.role = 'ASSISTANT' GROUP BY c.student_id, m.created_at::date;

    -- Caption files for popular videos, uploaded as LESSON_CAPTION files
    FOR v_row IN
        SELECT l.id, co.owner_id, co.slug, lang.code, lang.label
        FROM lessons l JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id
        CROSS JOIN (VALUES ('en', 'English'), ('km', 'ខ្មែរ')) AS lang(code, label)
        WHERE l.type = 'VIDEO' AND co.status = 'PUBLISHED' AND s.position = 0 AND (lang.code = 'en' OR random() < 0.35)
    LOOP
        INSERT INTO stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name, status, created_at, updated_at)
        VALUES (gen_random_uuid(), coalesce(v_row.owner_id, seed_tmp.admin_id()), 'LESSON_CAPTION',
                'private/captions/' || gen_random_uuid() || '.vtt', 'text/vtt', seed_tmp.rint(4000, 40000),
                v_row.slug || '-lesson-' || v_row.id || '-' || v_row.code || '.vtt', 'READY',
                now() - make_interval(days => seed_tmp.rint(10, 200)), now() - make_interval(days => seed_tmp.rint(1, 9)));
        INSERT INTO lesson_captions (lesson_id, language, label, file_ref, created_by, created_at)
        SELECT v_row.id, v_row.code, v_row.label, 'storage://' || f.object_key, f.uploaded_by, f.created_at
        FROM stored_files f WHERE f.original_name = v_row.slug || '-lesson-' || v_row.id || '-' || v_row.code || '.vtt';
    END LOOP;

    -- Every storage:// reference above has its stored file
    INSERT INTO stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name, status, created_at, updated_at)
    SELECT gen_random_uuid(), st.user_id, 'ASSIGNMENT_SUBMISSION', substr(a.file_url, 11), 'application/pdf', seed_tmp.rint(80000, 4000000),
           a.file_name, 'READY', a.submitted_at - interval '5 minutes', a.submitted_at
    FROM assignment_submissions a JOIN students st ON st.id = a.student_id WHERE a.file_url LIKE 'storage://%';
    INSERT INTO stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name, status, created_at, updated_at)
    SELECT gen_random_uuid(), st.user_id, 'TOP_UP_RECEIPT', substr(t.receipt_url, 11),
           CASE WHEN t.receipt_url LIKE '%.png' THEN 'image/png' ELSE 'image/jpeg' END, seed_tmp.rint(60000, 900000),
           t.receipt_name, 'READY', t.created_at - interval '2 minutes', t.created_at
    FROM member_card_top_up_requests t JOIN member_cards c ON c.id = t.card_id JOIN students st ON st.id = c.student_id
    WHERE t.receipt_url LIKE 'storage://%';
    -- An abandoned upload and an old replaced thumbnail the cleanup job will remove
    INSERT INTO stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name, status, created_at, updated_at)
    VALUES (gen_random_uuid(), seed_tmp.uid('visal.tan@demo.courseaurahub.io'), 'COURSE_THUMBNAIL', 'uploads/' || gen_random_uuid() || '.png',
            'image/png', 512000, 'new-cover.png', 'PENDING', now() - interval '3 hours', now() - interval '3 hours'),
           (gen_random_uuid(), seed_tmp.uid('lina.meas@demo.courseaurahub.io'), 'COURSE_THUMBNAIL', 'public/courses/' || gen_random_uuid() || '.jpg',
            'image/jpeg', 230000, 'old-cover.jpg', 'RELEASED', now() - interval '90 days', now() - interval '20 days'),
           (gen_random_uuid(), seed_tmp.uid('sokha.chan@demo.courseaurahub.io'), 'AVATAR', 'public/avatars/' || gen_random_uuid() || '.webp',
            'image/webp', 48000, 'me.webp', 'READY', now() - interval '200 days', now() - interval '200 days'),
           (gen_random_uuid(), seed_tmp.uid('sokha.chan@demo.courseaurahub.io'), 'LESSON_CONTENT', 'private/lessons/' || gen_random_uuid() || '.mp4',
            'video/mp4', 734003200, 'welcome-and-setup.mp4', 'READY', now() - interval '300 days', now() - interval '300 days');

    -- Transcoding history of earlier uploads (lessons now point at the YouTube video, so nothing is queued)
    INSERT INTO video_transcode_jobs (lesson_id, source_ref, status, attempts, error, created_at, updated_at)
    SELECT l.id, 'storage://private/lessons/' || gen_random_uuid() || '.mp4', x.status, x.attempts, x.error,
           now() - make_interval(days => seed_tmp.rint(30, 200)), now() - make_interval(days => seed_tmp.rint(1, 29))
    FROM (SELECT l.id, row_number() OVER (ORDER BY random()) AS rn FROM lessons l WHERE l.type = 'VIDEO') l
    CROSS JOIN LATERAL (SELECT CASE WHEN l.rn % 4 = 0 THEN 'FAILED' ELSE 'DONE' END AS status,
                               CASE WHEN l.rn % 4 = 0 THEN 3 ELSE 1 END AS attempts,
                               CASE WHEN l.rn % 4 = 0 THEN 'ffprobe could not read the uploaded file (moov atom not found)' END AS error) x
    WHERE l.rn <= 16;

    -- Nudges sent to learners who stopped part-way
    INSERT INTO learner_reminders (student_id, course_id, sent_at)
    SELECT sc.student_id, sc.course_id, now() - make_interval(days => seed_tmp.rint(1, 20))
    FROM student_courses sc JOIN students st ON st.id = sc.student_id
    WHERE NOT sc.completed AND st.reminders_enabled
      AND coalesce((SELECT max(lp.completed_at) FROM lesson_progress lp JOIN lessons l ON l.id = lp.lesson_id
                    JOIN course_sections s ON s.id = l.section_id WHERE lp.student_id = sc.student_id AND s.course_id = sc.course_id),
                   now() - interval '1 year') < now() - interval '21 days'
      AND random() < 0.4;
END $seed$;

-- ===============================================================================================================
-- 19. Chat: course channels, direct messages, private groups and invites, support, files, moderation
-- ===============================================================================================================
-- Members come only from real enrollments and course owners, groups have one owner, cursors never pass the last message
CREATE FUNCTION seed_tmp.chat_say(p_conv int, p_sender uuid, p_kind text, p_body text, p_at timestamptz)
    RETURNS int LANGUAGE plpgsql AS $fn$
DECLARE
    v_id int;
BEGIN
    INSERT INTO chat_messages (conversation_id, sender_id, client_message_id, kind, body, created_at)
    VALUES (p_conv, p_sender, CASE WHEN p_kind = 'TEXT' THEN gen_random_uuid() END, p_kind, p_body, p_at)
    RETURNING id INTO v_id;
    UPDATE chat_conversations SET last_message_id = v_id, last_activity_at = p_at WHERE id = p_conv;
    RETURN v_id;
END $fn$;

-- The name other learners see ("Jane L."), as DisplayNames.shortName writes it
CREATE FUNCTION seed_tmp.short_name(p_user uuid) RETURNS text LANGUAGE sql STABLE AS
$fn$ SELECT first_name || ' ' || left(last_name, 1) || '.' FROM users WHERE id = p_user $fn$;

DO $seed$
DECLARE
    v_course record;
    v_pair record;
    v_conv int;
    v_msg int;
    v_group int;
    v_owner uuid;
    v_members uuid[];
    v_sender uuid;
    v_at timestamptz;
    v_file uuid;
    v_admin uuid := seed_tmp.admin_id();
    v_learner uuid := seed_tmp.uid('sreymom.chea@demo.courseaurahub.io');
    v_channel text[] := ARRAY['Has anyone finished the section 2 exercise?', 'The checklist at the end of lesson 4 really helped.',
        'Is there a study group for this course?', 'I am stuck on the last quiz question, any hints?',
        'Thanks for the clear explanations!', 'Which tools are you all using for the project?',
        'Just got my certificate, good luck everyone!', 'Does the live class get recorded?'];
    v_direct text[] := ARRAY['Hi! Are you also taking this course?', 'Yes, I am on section 3 now.',
        'Want to review the quiz answers together?', 'Sure, tomorrow evening works for me.',
        'I shared my notes in our group.', 'Thanks, that helps a lot!'];
BEGIN
    -- Course channels for the three busiest published courses that have an owner
    FOR v_course IN
        SELECT co.id, co.owner_id
        FROM courses co
        WHERE co.status = 'PUBLISHED' AND co.owner_id IS NOT NULL
        ORDER BY (SELECT count(*) FROM student_courses sc WHERE sc.course_id = co.id) DESC, co.id
        LIMIT 3
    LOOP
        v_at := now() - interval '45 days';
        INSERT INTO chat_conversations (type, course_id, status, allow_member_invites, version, created_at, last_activity_at)
        VALUES ('COURSE', v_course.id, 'ACTIVE', false, 0, v_at, v_at) RETURNING id INTO v_conv;
        SELECT array_agg(user_id) INTO v_members FROM (
            SELECT st.user_id FROM student_courses sc JOIN students st ON st.id = sc.student_id JOIN users u ON u.id = st.user_id
            WHERE sc.course_id = v_course.id AND u.enabled ORDER BY random() LIMIT 12) m;
        v_members := array_prepend(v_course.owner_id, coalesce(v_members, ARRAY[]::uuid[]));
        INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
        SELECT v_conv, m, 'MEMBER', v_at + make_interval(hours => seed_tmp.rint(1, 200)), 0, 0, random() < 0.1
        FROM unnest(v_members) AS m;
        PERFORM seed_tmp.chat_say(v_conv, v_course.owner_id, 'TEXT',
                'Welcome to the course chat! Ask questions here and help each other out.', v_at + interval '1 hour');
        FOR k IN 1..9 LOOP
            v_sender := v_members[1 + floor(random() * array_length(v_members, 1))::int];
            v_msg := seed_tmp.chat_say(v_conv, v_sender, 'TEXT', seed_tmp.pick(v_channel), v_at + make_interval(days => k * 4));
        END LOOP;
        -- Course staff hid one off-topic message
        v_msg := seed_tmp.chat_say(v_conv, v_members[array_length(v_members, 1)], 'TEXT', 'Selling cheap course notes, DM me!',
                now() - interval '3 days');
        UPDATE chat_messages SET hidden_at = now() - interval '2 days', hidden_by = v_course.owner_id WHERE id = v_msg;
    END LOOP;

    -- Direct conversations between learners who share a course, and with a course owner
    FOR v_pair IN
        SELECT DISTINCT ON (pair_key) a, b FROM (
            SELECT least(sa.user_id, sb.user_id) AS a, greatest(sa.user_id, sb.user_id) AS b,
                   least(sa.user_id, sb.user_id)::text || ':' || greatest(sa.user_id, sb.user_id)::text AS pair_key
            FROM student_courses x JOIN student_courses y ON y.course_id = x.course_id AND y.student_id > x.student_id
            JOIN students sa ON sa.id = x.student_id JOIN students sb ON sb.id = y.student_id
            JOIN users ua ON ua.id = sa.user_id JOIN users ub ON ub.id = sb.user_id
            JOIN courses co ON co.id = x.course_id
            WHERE ua.enabled AND ub.enabled AND co.status = 'PUBLISHED'
            ORDER BY random() LIMIT 400) p
        LIMIT 14
    LOOP
        v_at := now() - make_interval(days => seed_tmp.rint(3, 40));
        INSERT INTO chat_conversations (type, direct_key, status, created_by, allow_member_invites, version, created_at, last_activity_at)
        VALUES ('DIRECT', v_pair.a::text || ':' || v_pair.b::text, 'ACTIVE', v_pair.a, false, 0, v_at, v_at)
        ON CONFLICT DO NOTHING RETURNING id INTO v_conv;
        CONTINUE WHEN v_conv IS NULL;
        INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
        VALUES (v_conv, v_pair.a, 'MEMBER', v_at, 0, 0, false), (v_conv, v_pair.b, 'MEMBER', v_at, 0, 0, false);
        FOR k IN 1..seed_tmp.rint(2, 6) LOOP
            PERFORM seed_tmp.chat_say(v_conv, CASE WHEN k % 2 = 1 THEN v_pair.a ELSE v_pair.b END, 'TEXT', v_direct[k],
                    v_at + make_interval(hours => k * 5));
        END LOOP;
    END LOOP;
    SELECT co.owner_id, st.user_id INTO v_owner, v_sender
    FROM courses co JOIN student_courses sc ON sc.course_id = co.id JOIN students st ON st.id = sc.student_id
    WHERE co.owner_id IS NOT NULL AND co.status = 'PUBLISHED' AND st.user_id IS NOT NULL
    ORDER BY co.id, sc.student_id LIMIT 1;
    v_at := now() - interval '6 days';
    INSERT INTO chat_conversations (type, direct_key, status, created_by, allow_member_invites, version, created_at, last_activity_at)
    VALUES ('DIRECT', least(v_owner, v_sender)::text || ':' || greatest(v_owner, v_sender)::text, 'ACTIVE', v_sender, false, 0, v_at, v_at)
    ON CONFLICT DO NOTHING RETURNING id INTO v_conv;
    IF v_conv IS NOT NULL THEN
        INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
        VALUES (v_conv, v_owner, 'MEMBER', v_at, 0, 0, false), (v_conv, v_sender, 'MEMBER', v_at, 0, 0, false);
        PERFORM seed_tmp.chat_say(v_conv, v_sender, 'TEXT', 'Could you look at my assignment feedback when you have time?', v_at);
        PERFORM seed_tmp.chat_say(v_conv, v_owner, 'TEXT', 'Of course. Your structure is good; tighten the conclusion.', v_at + interval '3 hours');
        -- A file sent with a message, uploaded as a CHAT_ATTACHMENT
        v_file := gen_random_uuid();
        INSERT INTO stored_files (id, uploaded_by, purpose, object_key, content_type, size_bytes, original_name, status, created_at, updated_at)
        VALUES (v_file, v_sender, 'CHAT_ATTACHMENT', 'private/chat/' || v_file || '.pdf', 'application/pdf', 182000,
                'assignment-draft-v2.pdf', 'READY', v_at + interval '5 hours', v_at + interval '5 hours');
        v_msg := seed_tmp.chat_say(v_conv, v_sender, 'TEXT', NULL, v_at + interval '5 hours');
        INSERT INTO chat_attachments (message_id, file_ref, file_name, content_type, size_bytes)
        VALUES (v_msg, 'storage://private/chat/' || v_file || '.pdf', 'assignment-draft-v2.pdf', 'application/pdf', 182000);
        -- One the sender took back
        v_msg := seed_tmp.chat_say(v_conv, v_sender, 'TEXT', NULL, v_at + interval '6 hours');
        UPDATE chat_messages SET deleted_at = v_at + interval '6 hours 2 minutes' WHERE id = v_msg;
    END IF;

    -- Private groups in the busiest course: an active one with every invite state, and an archived one
    SELECT co.id INTO v_course FROM courses co
    WHERE co.status = 'PUBLISHED' ORDER BY (SELECT count(*) FROM student_courses sc WHERE sc.course_id = co.id) DESC, co.id LIMIT 1;
    SELECT array_agg(user_id) INTO v_members FROM (
        SELECT st.user_id FROM student_courses sc JOIN students st ON st.id = sc.student_id JOIN users u ON u.id = st.user_id
        WHERE sc.course_id = v_course.id AND u.enabled ORDER BY st.id LIMIT 9) m;
    v_owner := v_members[1];
    v_at := now() - interval '20 days';
    INSERT INTO chat_conversations (type, course_id, title, description, owner_id, allow_member_invites, status, created_by,
                                    version, created_at, last_activity_at)
    VALUES ('GROUP', v_course.id, 'Weekend study group', 'We meet on Saturdays to go through the week''s lessons.', v_owner,
            true, 'ACTIVE', v_owner, 2, v_at, v_at)
    RETURNING id INTO v_group;
    INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
    VALUES (v_group, v_owner, 'OWNER', v_at, 0, 0, false);
    PERFORM seed_tmp.chat_say(v_group, NULL, 'SYSTEM', seed_tmp.short_name(v_owner) || ' created the group', v_at);
    FOR k IN 2..5 LOOP
        INSERT INTO chat_group_invites (conversation_id, invitee_id, inviter_id, status, created_at, expires_at, responded_at)
        VALUES (v_group, v_members[k], v_owner, 'ACCEPTED', v_at + interval '1 hour', v_at + interval '7 days 1 hour',
                v_at + make_interval(hours => k * 3));
        INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
        VALUES (v_group, v_members[k], CASE WHEN k = 2 THEN 'ADMIN' ELSE 'MEMBER' END, v_at + make_interval(hours => k * 3), 0, 0, k = 5);
        PERFORM seed_tmp.chat_say(v_group, NULL, 'SYSTEM', seed_tmp.short_name(v_members[k]) || ' joined', v_at + make_interval(hours => k * 3));
    END LOOP;
    PERFORM seed_tmp.chat_say(v_group, v_owner, 'TEXT', 'Saturday 10am, lesson 5 and 6. Bring your questions!', v_at + interval '2 days');
    PERFORM seed_tmp.chat_say(v_group, v_members[2], 'TEXT', 'I will prepare a summary of lesson 5.', v_at + interval '2 days 1 hour');
    PERFORM seed_tmp.chat_say(v_group, v_members[3], 'TEXT', 'Great, see you there.', v_at + interval '2 days 3 hours');
    -- A member who left, and invites still open, declined and expired
    UPDATE chat_participants SET left_at = v_at + interval '10 days', left_reason = 'LEFT'
    WHERE conversation_id = v_group AND user_id = v_members[5];
    PERFORM seed_tmp.chat_say(v_group, NULL, 'SYSTEM', seed_tmp.short_name(v_members[5]) || ' left', v_at + interval '10 days');
    INSERT INTO chat_group_invites (conversation_id, invitee_id, inviter_id, status, created_at, expires_at, responded_at)
    VALUES (v_group, v_members[6], v_members[2], 'PENDING', now() - interval '1 day', now() + interval '6 days', NULL),
           (v_group, v_members[7], v_owner, 'DECLINED', v_at + interval '1 day', v_at + interval '8 days', v_at + interval '2 days'),
           (v_group, v_members[8], v_owner, 'EXPIRED', v_at, v_at + interval '7 days', v_at + interval '7 days');

    v_at := now() - interval '90 days';
    INSERT INTO chat_conversations (type, course_id, title, owner_id, allow_member_invites, status, created_by, version,
                                    created_at, last_activity_at)
    VALUES ('GROUP', v_course.id, 'Exam cram (finished)', v_members[9], false, 'ARCHIVED', v_members[9], 1, v_at, v_at)
    RETURNING id INTO v_group;
    INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
    VALUES (v_group, v_members[9], 'OWNER', v_at, 0, 0, false), (v_group, v_owner, 'MEMBER', v_at + interval '2 hours', 0, 0, false);
    PERFORM seed_tmp.chat_say(v_group, NULL, 'SYSTEM', seed_tmp.short_name(v_members[9]) || ' created the group', v_at);
    PERFORM seed_tmp.chat_say(v_group, v_members[9], 'TEXT', 'Good luck on the exam tomorrow!', v_at + interval '1 day');
    PERFORM seed_tmp.chat_say(v_group, NULL, 'SYSTEM', seed_tmp.short_name(v_members[9]) || ' archived the group', v_at + interval '3 days');

    -- Support: an open conversation the admins answered, and a closed one
    v_at := now() - interval '2 days';
    INSERT INTO chat_conversations (type, status, created_by, allow_member_invites, version, created_at, last_activity_at)
    VALUES ('SUPPORT', 'OPEN', v_learner, false, 0, v_at, v_at) RETURNING id INTO v_conv;
    INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
    VALUES (v_conv, v_learner, 'MEMBER', v_at, 0, 0, false);
    PERFORM seed_tmp.chat_say(v_conv, v_learner, 'TEXT', 'My member card top-up from yesterday is not showing yet.', v_at);
    PERFORM seed_tmp.chat_say(v_conv, v_admin, 'TEXT', 'Thanks for the receipt, we are checking it with the bank now.', v_at + interval '2 hours');
    v_sender := v_members[3];
    v_at := now() - interval '30 days';
    INSERT INTO chat_conversations (type, status, created_by, allow_member_invites, version, created_at, last_activity_at)
    VALUES ('SUPPORT', 'CLOSED', v_sender, false, 0, v_at, v_at) RETURNING id INTO v_conv;
    INSERT INTO chat_participants (conversation_id, user_id, role, joined_at, last_read_message_id, last_delivered_message_id, muted)
    VALUES (v_conv, v_sender, 'MEMBER', v_at, 0, 0, false);
    PERFORM seed_tmp.chat_say(v_conv, v_sender, 'TEXT', 'How do I change the email on my account?', v_at);
    PERFORM seed_tmp.chat_say(v_conv, v_admin, 'TEXT', 'Go to Settings, then Account, and choose Change email.', v_at + interval '1 hour');
    PERFORM seed_tmp.chat_say(v_conv, NULL, 'SYSTEM', 'Support closed this conversation', v_at + interval '1 day');

    -- Read and delivery cursors: most people are up to date, some are behind
    UPDATE chat_participants p
    SET last_delivered_message_id = coalesce(cv.last_message_id, 0),
        last_read_message_id = CASE WHEN random() < 0.7 THEN coalesce(cv.last_message_id, 0)
                                    ELSE coalesce((SELECT max(m.id) FROM chat_messages m
                                                   WHERE m.conversation_id = p.conversation_id
                                                     AND m.created_at < cv.last_activity_at - interval '1 day'), 0) END
    FROM chat_conversations cv WHERE cv.id = p.conversation_id;
    -- Senders have always read what they wrote
    UPDATE chat_participants p
    SET last_read_message_id = greatest(p.last_read_message_id,
            (SELECT max(m.id) FROM chat_messages m WHERE m.conversation_id = p.conversation_id AND m.sender_id = p.user_id))
    WHERE EXISTS (SELECT 1 FROM chat_messages m WHERE m.conversation_id = p.conversation_id AND m.sender_id = p.user_id);

    -- Contact settings, a block, a chat restriction and a reported message
    INSERT INTO chat_settings (user_id, direct_messages)
    SELECT user_id, (ARRAY['STAFF_ONLY', 'NOBODY', 'COURSE_MEMBERS'])[1 + row_number() OVER (ORDER BY user_id) % 3]
    FROM (SELECT user_id FROM seed_tmp.people WHERE kind = 'bulk' ORDER BY random() LIMIT 12) s;
    INSERT INTO chat_blocks (blocker_id, blocked_id, created_at)
    SELECT a, b, now() - interval '12 days' FROM (
        SELECT p.user_id AS a, lead(p.user_id) OVER (ORDER BY p.user_id) AS b
        FROM seed_tmp.people p WHERE p.kind = 'bulk') x
    WHERE b IS NOT NULL LIMIT 3;
    INSERT INTO chat_restrictions (user_id, until, reason, created_by, created_at)
    SELECT m.sender_id, now() + interval '5 days', 'Advertising in the course chat', v_admin, now() - interval '2 days'
    FROM chat_messages m WHERE m.hidden_at IS NOT NULL ORDER BY m.id LIMIT 1;
    INSERT INTO content_reports (target_type, target_id, reporter_id, reason, status, resolved_by, resolved_at, created_at)
    SELECT 'CHAT_MESSAGE', m.id, p.user_id, 'Spam or advertising', 'HIDDEN', v_admin, m.hidden_at, m.created_at + interval '1 hour'
    FROM chat_messages m
    JOIN chat_participants p ON p.conversation_id = m.conversation_id AND p.user_id <> m.sender_id
    WHERE m.hidden_at IS NOT NULL
    ORDER BY m.id, p.user_id LIMIT 1;
    INSERT INTO content_reports (target_type, target_id, reporter_id, reason, status, created_at)
    SELECT 'CHAT_MESSAGE', m.id, p.user_id, 'Rude message', 'OPEN', m.created_at + interval '30 minutes'
    FROM chat_messages m
    JOIN chat_conversations cv ON cv.id = m.conversation_id AND cv.type = 'DIRECT'
    JOIN chat_participants p ON p.conversation_id = m.conversation_id AND p.user_id <> m.sender_id
    WHERE m.kind = 'TEXT' AND m.body IS NOT NULL
    ORDER BY m.id DESC LIMIT 1;
END $seed$;

-- ===============================================================================================================
-- 20. Notifications, email preferences, browser push, Telegram
-- ===============================================================================================================
CREATE FUNCTION seed_tmp.read_at(p_at timestamptz) RETURNS timestamptz LANGUAGE sql VOLATILE AS
$fn$ SELECT CASE WHEN p_at < now() - interval '4 days' AND random() < 0.85 THEN p_at + make_interval(hours => seed_tmp.rint(1, 30))
                 WHEN random() < 0.25 THEN least(p_at + interval '30 minutes', now()) END $fn$;

CREATE TABLE seed_tmp.notes (user_id uuid, type text, title text, message text, link text, at timestamptz);

INSERT INTO seed_tmp.notes
SELECT st.user_id, 'ENROLLED', 'You''re enrolled', co.title || ' is ready. Start with the first lesson.', '/learn/' || co.slug,
       e.enrolled_at + time '10:00'
FROM enrollments e JOIN students st ON st.id = e.student_id AND st.user_id IS NOT NULL JOIN courses co ON co.id = e.course_id;
INSERT INTO seed_tmp.notes
SELECT co.owner_id, 'NEW_ENROLLMENT', 'New learner', st.name || ' enrolled in ' || co.title || '.', '/instructor/courses/' || co.slug,
       e.enrolled_at + time '10:05'
FROM enrollments e JOIN students st ON st.id = e.student_id JOIN courses co ON co.id = e.course_id
WHERE co.owner_id IS NOT NULL AND e.enrolled_at > current_date - 90;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'CERTIFICATE_ISSUED', 'Certificate earned', 'You completed ' || cc.course_title || '. Your certificate is ready.',
       '/certificates/' || cc.certificate_number, cc.issued_at
FROM course_certificates cc JOIN students st ON st.id = cc.student_id WHERE st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'CERTIFICATE_ISSUED', 'Learning path completed', 'You completed every course in ' || pc.path_title || '. Your path certificate is ready.',
       '/certificates/' || pc.certificate_number, pc.issued_at + interval '1 minute'
FROM learning_path_certificates pc JOIN students st ON st.id = pc.student_id WHERE st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT coalesce(co.owner_id, a.id), 'COURSE_COMPLETED', 'Course completed', cc.learner_name || ' completed ' || cc.course_title || '.',
       '/courses/' || co.slug, cc.issued_at + interval '1 minute'
FROM course_certificates cc JOIN courses co ON co.id = cc.course_id
CROSS JOIN LATERAL (SELECT id FROM users WHERE role = 'ROLE_ADMIN' AND co.owner_id IS NULL ORDER BY email LIMIT 1) a
WHERE cc.issued_at > now() - interval '120 days';
INSERT INTO seed_tmp.notes
SELECT co.owner_id, 'COURSE_COMPLETED', 'Course completed', cc.learner_name || ' completed ' || cc.course_title || '.',
       '/courses/' || co.slug, cc.issued_at + interval '1 minute'
FROM course_certificates cc JOIN courses co ON co.id = cc.course_id
WHERE co.owner_id IS NOT NULL AND cc.issued_at > now() - interval '120 days';
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'ORDER_REFUNDED', 'Order refunded', 'Your payment of $' || o.amount || ' for ' || co.title || ' was refunded.', '/orders', o.refunded_at
FROM orders o JOIN students st ON st.id = o.student_id JOIN courses co ON co.id = o.course_id WHERE o.status = 'REFUNDED' AND st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'PAYMENT_FAILED', 'Payment failed', 'Your payment for ' || co.title || ' did not go through. You can try again from your orders.',
       '/orders', o.created_at + interval '2 minutes'
FROM orders o JOIN students st ON st.id = o.student_id JOIN courses co ON co.id = o.course_id WHERE o.status = 'FAILED' AND st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT u.id, 'REFUND_REQUEST', 'New refund request', st.name || ' asked for a refund of ' || co.title || '.', '/admin/refund-requests', r.created_at
FROM refund_requests r JOIN students st ON st.id = r.student_id JOIN orders o ON o.id = r.order_id JOIN courses co ON co.id = o.course_id
CROSS JOIN users u WHERE u.role = 'ROLE_ADMIN';
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'REFUND_REQUEST', CASE r.status WHEN 'APPROVED' THEN 'Refund approved' ELSE 'Refund request declined' END,
       CASE r.status WHEN 'APPROVED' THEN 'Your refund for ' || co.title || ' was approved.' ELSE coalesce(r.decision_note, 'Your refund request was declined.') END,
       '/orders', r.reviewed_at
FROM refund_requests r JOIN students st ON st.id = r.student_id JOIN orders o ON o.id = r.order_id JOIN courses co ON co.id = o.course_id
WHERE r.status <> 'PENDING' AND r.reviewed_at IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'SUBSCRIPTION',
       CASE sp.status WHEN 'PAID' THEN 'Subscription renewed' WHEN 'FAILED' THEN 'Renewal payment failed' ELSE 'Finish your subscription' END,
       CASE sp.status WHEN 'PAID' THEN 'Your ' || pl.name || ' plan is active until ' || to_char(sp.period_end, 'DD Mon YYYY') || '.'
                      WHEN 'FAILED' THEN 'We could not charge your ' || pl.name || ' plan. Update your payment to keep access.'
                      ELSE 'Complete the payment to start your ' || pl.name || ' plan.' END,
       '/subscription', coalesce(sp.paid_at, sp.created_at) + interval '1 minute'
FROM subscription_payments sp JOIN subscriptions s ON s.id = sp.subscription_id JOIN subscription_plans pl ON pl.id = s.plan_id
JOIN students st ON st.id = s.student_id;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'MEMBER_CARD', CASE t.status WHEN 'APPROVED' THEN 'Top-up approved' ELSE 'Top-up not approved' END,
       CASE t.status WHEN 'APPROVED' THEN 'Your member card was topped up with $' || t.amount || '.' ELSE t.review_note END,
       '/member-card', t.reviewed_at
FROM member_card_top_up_requests t JOIN member_cards c ON c.id = t.card_id JOIN students st ON st.id = c.student_id
WHERE t.status <> 'PENDING';
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'MEMBER_CARD', CASE a.status WHEN 'APPROVED' THEN 'Member card approved' ELSE 'Member card application not approved' END,
       CASE a.status WHEN 'APPROVED' THEN 'Your member card is ready. Top it up to pay for courses.' ELSE a.review_note END,
       '/member-card', a.reviewed_at
FROM member_card_applications a JOIN students st ON st.id = a.student_id WHERE a.status <> 'PENDING';
INSERT INTO seed_tmp.notes
SELECT u.id, 'MEMBER_CARD', 'New member card application', st.name || ' applied for a member card.', '/admin/member-cards', a.created_at
FROM member_card_applications a JOIN students st ON st.id = a.student_id CROSS JOIN users u WHERE u.role = 'ROLE_ADMIN' AND a.created_at > now() - interval '60 days';
INSERT INTO seed_tmp.notes
SELECT u.id, 'MEMBER_CARD', 'Top-up waiting for approval', st.name || ' asked to top up $' || t.amount || '.', '/admin/member-cards', t.created_at
FROM member_card_top_up_requests t JOIN member_cards c ON c.id = t.card_id JOIN students st ON st.id = c.student_id
CROSS JOIN users u WHERE u.role = 'ROLE_ADMIN' AND t.status = 'PENDING';
INSERT INTO seed_tmp.notes
SELECT p.instructor_id, 'PAYOUT', CASE p.status WHEN 'PAID' THEN 'Payout sent' WHEN 'APPROVED' THEN 'Payout approved' ELSE 'Payout declined' END,
       CASE p.status WHEN 'PAID' THEN 'We sent $' || p.amount || ' to your ' || p.method || ' account.'
                     WHEN 'APPROVED' THEN 'Your payout of $' || p.amount || ' was approved and will be sent soon.'
                     ELSE p.decision_note END,
       '/instructor/earnings', coalesce(p.paid_at, p.reviewed_at)
FROM payout_requests p WHERE p.status IN ('PAID', 'APPROVED', 'REJECTED');
INSERT INTO seed_tmp.notes
SELECT u.id, 'PAYOUT', 'Payout requested', i.first_name || ' ' || i.last_name || ' requested a payout of $' || p.amount || '.', '/admin/payouts', p.created_at
FROM payout_requests p JOIN users i ON i.id = p.instructor_id CROSS JOIN users u WHERE u.role = 'ROLE_ADMIN' AND p.status = 'REQUESTED';
INSERT INTO seed_tmp.notes
SELECT coalesce(co.owner_id, (SELECT id FROM users WHERE email = 'admin@demo.courseaurahub.io')), 'LESSON_QUESTION', 'New question',
       q.author_name || ' asked a question in ' || l.title || '.', '/instructor/questions', q.created_at
FROM lesson_questions q JOIN lessons l ON l.id = q.lesson_id JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id;
INSERT INTO seed_tmp.notes
SELECT q.author_id, 'LESSON_QUESTION', 'New answer to your question', a.author_name || ' answered your question in ' || l.title || '.',
       '/learn/' || co.slug || '?lesson=' || l.id, a.created_at
FROM lesson_answers a JOIN lesson_questions q ON q.id = a.question_id JOIN lessons l ON l.id = q.lesson_id
JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id
WHERE q.author_id IS NOT NULL AND a.author_id IS DISTINCT FROM q.author_id;
INSERT INTO seed_tmp.notes
SELECT coalesce(co.owner_id, (SELECT id FROM users WHERE email = 'admin@demo.courseaurahub.io')), 'ASSIGNMENT', 'New submission to grade',
       st.name || ' submitted the ' || co.title || ' project.', '/instructor/submissions', sub.submitted_at
FROM assignment_submissions sub JOIN students st ON st.id = sub.student_id JOIN assignments asg ON asg.id = sub.assignment_id
JOIN lessons l ON l.id = asg.lesson_id JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'ASSIGNMENT', 'Assignment graded', 'Your project in ' || co.title || ' scored ' || sub.score || '%.',
       '/learn/' || co.slug || '?lesson=' || l.id, sub.graded_at
FROM assignment_submissions sub JOIN students st ON st.id = sub.student_id JOIN assignments asg ON asg.id = sub.assignment_id
JOIN lessons l ON l.id = asg.lesson_id JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id
WHERE sub.status = 'GRADED' AND st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'ASSIGNMENT', 'Extra quiz attempt', 'You were given another attempt at ' || l.title || '.',
       '/learn/' || co.slug || '?lesson=' || l.id, g.updated_at
FROM quiz_attempt_grants g JOIN students st ON st.id = g.student_id JOIN lessons l ON l.id = g.lesson_id
JOIN course_sections s ON s.id = l.section_id JOIN courses co ON co.id = s.course_id WHERE st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT co.owner_id, 'COURSE_REVIEW', x.title, x.message, '/instructor/courses/' || co.slug, x.at
FROM courses co
CROSS JOIN LATERAL (SELECT CASE co.status WHEN 'PUBLISHED' THEN 'Course approved' WHEN 'DRAFT' THEN 'Changes requested' ELSE 'Course sent for review' END AS title,
                           CASE co.status WHEN 'PUBLISHED' THEN co.title || ' is now live.' WHEN 'DRAFT' THEN 'An admin sent ' || co.title || ' back with notes.'
                                ELSE co.title || ' is waiting for an admin.' END AS message,
                           co.created_at + interval '3 days 10 hours' AS at) x
WHERE co.owner_id IS NOT NULL AND (co.status <> 'DRAFT' OR co.review_note IS NOT NULL);
INSERT INTO seed_tmp.notes
SELECT u.id, 'COURSE_REVIEW', 'Course waiting for review', co.instructor || ' sent ' || co.title || ' for review.', '/admin/course-reviews',
       co.created_at + interval '1 day 9 hours'
FROM courses co CROSS JOIN users u WHERE u.role = 'ROLE_ADMIN' AND co.status = 'PENDING_REVIEW';
INSERT INTO seed_tmp.notes
SELECT u.id, 'COURSE_REVIEW', 'Course changes waiting for review', co.instructor || ' proposed changes to ' || co.title || '.', '/admin/course-reviews', r.created_at
FROM course_revisions r JOIN courses co ON co.id = r.course_id CROSS JOIN users u WHERE u.role = 'ROLE_ADMIN' AND r.status = 'PENDING';
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'COURSE_SURVEY', 'How was ' || co.title || '?', 'Tell us what you think in a one-minute survey.', '/learn/' || co.slug || '?survey=1', sp.prompted_at
FROM survey_prompts sp JOIN course_surveys sv ON sv.id = sp.survey_id JOIN courses co ON co.id = sv.course_id JOIN students st ON st.id = sp.student_id
WHERE st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'COURSE_REMINDER', 'Pick up where you left off', 'You are part-way through ' || co.title || '. Keep going!', '/learn/' || co.slug, r.sent_at
FROM learner_reminders r JOIN students st ON st.id = r.student_id JOIN courses co ON co.id = r.course_id WHERE st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'ANNOUNCEMENT', co.title || ': ' || a.title, a.body, '/learn/' || co.slug || '?announcement=' || a.id, a.created_at
FROM course_announcements a JOIN courses co ON co.id = a.course_id JOIN enrollments e ON e.course_id = a.course_id AND e.enrolled_at <= a.created_at::date
JOIN students st ON st.id = e.student_id WHERE st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'LIVE_SESSION', CASE WHEN ls.cancelled THEN 'Live class cancelled' ELSE 'Live class scheduled' END,
       ls.title || ' on ' || to_char(ls.starts_at, 'DD Mon at HH24:MI') || ' (UTC).', '/learn/' || co.slug || '?live=' || ls.id,
       ls.created_at
FROM live_sessions ls JOIN courses co ON co.id = ls.course_id JOIN enrollments e ON e.course_id = ls.course_id
JOIN students st ON st.id = e.student_id WHERE st.user_id IS NOT NULL AND ls.starts_at > now() - interval '7 days';
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'NEW_LESSON', 'New lesson', 'A new lesson was added to ' || co.title || '.', '/learn/' || co.slug, now() - make_interval(days => seed_tmp.rint(3, 40))
FROM student_courses sc JOIN students st ON st.id = sc.student_id JOIN courses co ON co.id = sc.course_id
WHERE NOT sc.completed AND st.user_id IS NOT NULL AND random() < 0.15;
INSERT INTO seed_tmp.notes
SELECT st.user_id, 'COURSE_UPDATED', 'Course updated', co.title || ' has a clearer title and description.', '/learn/' || co.slug, r.reviewed_at
FROM course_revisions r JOIN courses co ON co.id = r.course_id JOIN student_courses sc ON sc.course_id = co.id JOIN students st ON st.id = sc.student_id
WHERE r.status = 'APPROVED' AND st.user_id IS NOT NULL;
INSERT INTO seed_tmp.notes
SELECT u.id, 'WEEKLY_REPORT', 'Your weekly report', 'New enrollments, revenue and completions for the week of ' || to_char(w, 'DD Mon') || '.', '/analytics',
       w + interval '8 hours'
FROM users u CROSS JOIN generate_series(date_trunc('week', now()) - interval '12 weeks', date_trunc('week', now()), interval '1 week') AS w
WHERE u.role IN ('ROLE_ADMIN', 'ROLE_INSTRUCTOR') AND w + interval '8 hours' < now();

INSERT INTO notifications (user_id, type, title, message, link, read_at, created_at, emailed_at)
SELECT n.user_id, n.type, n.title, coalesce(n.message, n.title), n.link, seed_tmp.read_at(n.at), n.at, n.at + interval '1 minute'
FROM seed_tmp.notes n WHERE n.user_id IS NOT NULL AND n.at IS NOT NULL AND n.at <= now()
ORDER BY n.at;

-- Email preferences, browser push and Telegram
INSERT INTO notification_email_preferences (user_id, type, mode, updated_at)
SELECT p.user_id, t.type, seed_tmp.pick(ARRAY['INSTANT', 'DIGEST', 'DIGEST', 'OFF']), now() - make_interval(days => seed_tmp.rint(1, 100))
FROM seed_tmp.people p
CROSS JOIN unnest(ARRAY['ANNOUNCEMENT', 'COURSE_REMINDER', 'NEW_LESSON', 'LESSON_QUESTION', 'WEEKLY_REPORT', 'LIVE_SESSION', 'COURSE_SURVEY']) AS t(type)
WHERE (p.kind IN ('persona', 'instructor', 'admin') AND p.email NOT LIKE 'pending.%') OR (p.kind = 'bulk' AND random() < 0.12)
  AND random() < 0.6;
INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, user_agent, created_at, last_success_at)
SELECT p.user_id, 'https://fcm.googleapis.com/fcm/send/' || seed_tmp.b64(22) || ':' || seed_tmp.b64(120),
       'B' || seed_tmp.b64(86), seed_tmp.b64(22),
       seed_tmp.pick(ARRAY['Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36',
                           'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36',
                           'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36']),
       now() - make_interval(days => seed_tmp.rint(5, 120)), now() - make_interval(hours => seed_tmp.rint(1, 200))
FROM seed_tmp.people p
WHERE p.email IN ('vannak.kim@demo.courseaurahub.io', 'sokha.chan@demo.courseaurahub.io', 'admin@demo.courseaurahub.io', 'sreymom.chea@demo.courseaurahub.io')
   OR (p.kind = 'bulk' AND random() < 0.15);
INSERT INTO push_muted_types (user_id, type)
SELECT DISTINCT ps.user_id, t FROM push_subscriptions ps
CROSS JOIN unnest(ARRAY['WEEKLY_REPORT', 'COURSE_REMINDER', 'NEW_LESSON', 'ANNOUNCEMENT']) AS t
WHERE random() < 0.25;
INSERT INTO telegram_links (user_id, chat_id, telegram_name, enabled, linked_at)
SELECT p.user_id, 5000000000 + row_number() OVER (ORDER BY p.email) * 7919, '@' || replace(split_part(p.email, '@', 1), '.', '_'),
       p.kind <> 'bulk' OR random() < 0.6, now() - make_interval(days => seed_tmp.rint(3, 150))
FROM seed_tmp.people p
WHERE p.email IN ('vannak.kim@demo.courseaurahub.io', 'sokha.chan@demo.courseaurahub.io', 'admin@demo.courseaurahub.io')
   OR (p.kind = 'bulk' AND random() < 0.08);

-- ===============================================================================================================
-- 21. Auth: sessions, one-time tokens, OAuth identities, audit trail
-- ===============================================================================================================
DO $seed$
DECLARE
    v_person record;
    v_family uuid;
    v_prev uuid;
    v_id uuid;
    v_at timestamptz;
    v_agents text[] := ARRAY['Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36',
                             'Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1',
                             'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15',
                             'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36',
                             'Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0'];
    v_agent text;
    v_ip text;
BEGIN
    FOR v_person IN
        SELECT p.*, u.enabled, u.email_verified, u.provider FROM seed_tmp.people p JOIN users u ON u.id = p.user_id
        WHERE u.email_verified AND (p.kind <> 'bulk' OR random() < 0.45)
    LOOP
        -- One session family per device: an active one with its rotated predecessors, and a logged-out one
        FOR f IN 1..2 LOOP
            v_family := gen_random_uuid();
            v_prev := NULL;
            v_agent := seed_tmp.pick(v_agents);
            v_ip := '10.' || seed_tmp.rint(0, 255) || '.' || seed_tmp.rint(0, 255) || '.' || seed_tmp.rint(1, 254);
            v_at := greatest(v_person.joined, now() - interval '25 days') + make_interval(hours => seed_tmp.rint(1, 48));
            CONTINUE WHEN v_at > now() - interval '30 minutes';
            FOR k IN 1..seed_tmp.rint(1, 4) LOOP
                v_id := gen_random_uuid();
                IF v_prev IS NOT NULL THEN
                    UPDATE refresh_sessions SET revoked_at = v_at, revoked_reason = 'ROTATED', replaced_by = v_id WHERE id = v_prev;
                END IF;
                INSERT INTO refresh_sessions (id, user_id, family_id, token_hash, remember_me, session_started_at, created_at, last_used_at,
                                              expires_at, user_agent, ip_address)
                VALUES (v_id, v_person.user_id, v_family, seed_tmp.hex(64), f = 1, v_at - make_interval(hours => (k - 1) * 20), v_at, v_at,
                        v_at + CASE WHEN f = 1 THEN interval '30 days' ELSE interval '7 days' END, v_agent, v_ip);
                v_prev := v_id;
                v_at := v_at + make_interval(hours => seed_tmp.rint(4, 30));
                EXIT WHEN v_at > now() - interval '10 minutes';
            END LOOP;
            IF f = 2 OR NOT v_person.enabled THEN
                UPDATE refresh_sessions SET revoked_at = least(v_at, now()), revoked_reason = CASE WHEN v_person.enabled THEN 'LOGOUT' ELSE 'ACCOUNT_DISABLED' END
                WHERE id = v_prev;
            END IF;
        END LOOP;
    END LOOP;
    -- Sessions older than their expiry are expired, not active
    UPDATE refresh_sessions SET revoked_at = expires_at, revoked_reason = 'SESSION_LIMIT' WHERE revoked_at IS NULL AND expires_at < now();

    -- One-time codes: the unverified sign-up's live code, consumed verification and reset codes for others
    INSERT INTO auth_one_time_tokens (id, user_id, purpose, token_hash, attempts, max_attempts, remember_me, created_at, expires_at, consumed_at, revoked_at)
    SELECT gen_random_uuid(), u.id, 'EMAIL_VERIFICATION', seed_tmp.b64(43), 0, 5, false, now() - interval '2 hours', now() + interval '13 hours', NULL, NULL
    FROM users u WHERE u.email = 'pending.signup@demo.courseaurahub.io';
    INSERT INTO auth_one_time_tokens (id, user_id, purpose, token_hash, attempts, max_attempts, remember_me, created_at, expires_at, consumed_at, revoked_at)
    SELECT gen_random_uuid(), u.id, 'EMAIL_VERIFICATION', seed_tmp.b64(43), seed_tmp.rint(0, 1), 5, false, u.created_at, u.created_at + interval '15 minutes',
           u.created_at + interval '4 minutes', NULL
    FROM users u WHERE u.provider = 'LOCAL' AND u.email_verified;
    INSERT INTO auth_one_time_tokens (id, user_id, purpose, token_hash, attempts, max_attempts, remember_me, created_at, expires_at, consumed_at, revoked_at)
    SELECT gen_random_uuid(), u.id, 'PASSWORD_RESET', seed_tmp.b64(43), 0, 1, false, x.at, x.at + interval '30 minutes',
           CASE WHEN random() < 0.7 THEN x.at + interval '6 minutes' END, NULL
    FROM users u CROSS JOIN LATERAL (SELECT u.created_at + random() * (now() - interval '1 day' - u.created_at) AS at) x
    WHERE u.provider = 'LOCAL' AND u.email_verified AND u.created_at < now() - interval '2 days' AND random() < 0.12;
    INSERT INTO auth_one_time_tokens (id, user_id, purpose, token_hash, attempts, max_attempts, remember_me, created_at, expires_at, consumed_at, revoked_at)
    SELECT gen_random_uuid(), tl.user_id, 'TELEGRAM_LINK', seed_tmp.b64(43), 0, 1, false, tl.linked_at - interval '2 minutes',
           tl.linked_at + interval '8 minutes', tl.linked_at, NULL
    FROM telegram_links tl;

    -- OAuth identities: accounts created by a provider, and password accounts that linked one
    INSERT INTO user_oauth_identities (id, user_id, provider, provider_user_id, email, email_verified, created_at, last_login_at)
    SELECT gen_random_uuid(), u.id, u.provider, u.provider_id, u.email, true, u.created_at, now() - make_interval(days => seed_tmp.rint(0, 20))
    FROM users u WHERE u.provider <> 'LOCAL';
    INSERT INTO user_oauth_identities (id, user_id, provider, provider_user_id, email, email_verified, created_at, last_login_at)
    SELECT gen_random_uuid(), u.id, x.provider, seed_tmp.rint(10000000, 99999999)::text || seed_tmp.rint(1000, 9999), u.email,
           x.provider <> 'FACEBOOK', u.created_at + interval '20 days', now() - make_interval(days => seed_tmp.rint(0, 30))
    FROM users u
    CROSS JOIN LATERAL (SELECT CASE WHEN u.email = 'chantha.mao@demo.courseaurahub.io' THEN 'GITHUB'
                                    ELSE seed_tmp.pick(ARRAY['GOOGLE', 'GOOGLE', 'GITHUB', 'FACEBOOK']) END AS provider) x
    WHERE u.provider = 'LOCAL' AND u.email_verified
      AND (u.email IN ('chantha.mao@demo.courseaurahub.io', 'vannak.kim@demo.courseaurahub.io') OR (u.role = 'ROLE_USER' AND random() < 0.1));

    -- The sign-in audit trail
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, session_id, ip_address, user_agent, reason, metadata, created_at)
    SELECT 'REGISTRATION', 'SUCCESS', u.id, u.email, NULL, '10.0.' || seed_tmp.rint(0, 255) || '.' || seed_tmp.rint(1, 254),
           seed_tmp.pick(v_agents), NULL, '{"method":"' || CASE WHEN u.provider = 'LOCAL' THEN 'PASSWORD' ELSE u.provider END || '"}', u.created_at
    FROM users u;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, metadata, created_at)
    SELECT 'EMAIL_VERIFICATION', 'SUCCESS', u.id, u.email, '10.0.' || seed_tmp.rint(0, 255) || '.' || seed_tmp.rint(1, 254), seed_tmp.pick(v_agents), NULL,
           u.email_verified_at
    FROM users u WHERE u.email_verified AND u.provider = 'LOCAL';
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, session_id, ip_address, user_agent, metadata, created_at)
    SELECT CASE WHEN u.provider = 'LOCAL' THEN 'LOGIN' ELSE 'OAUTH2_LOGIN' END, 'SUCCESS', rs.user_id, u.email, rs.family_id, rs.ip_address, rs.user_agent,
           '{"rememberMe":' || rs.remember_me || '}', rs.session_started_at
    FROM refresh_sessions rs JOIN users u ON u.id = rs.user_id
    WHERE rs.replaced_by IS NULL OR rs.session_started_at = rs.created_at;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, session_id, ip_address, user_agent, created_at)
    SELECT 'SESSION_CREATED', 'SUCCESS', rs.user_id, u.email, rs.id, rs.ip_address, rs.user_agent, rs.created_at
    FROM refresh_sessions rs JOIN users u ON u.id = rs.user_id WHERE random() < 0.5;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, session_id, ip_address, user_agent, reason, created_at)
    SELECT 'SESSION_REVOKED', 'SUCCESS', rs.user_id, u.email, rs.id, rs.ip_address, rs.user_agent, rs.revoked_reason, rs.revoked_at
    FROM refresh_sessions rs JOIN users u ON u.id = rs.user_id WHERE rs.revoked_reason IN ('LOGOUT', 'ACCOUNT_DISABLED');
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, reason, created_at)
    SELECT 'LOGIN', 'FAILURE', u.id, u.email, '10.1.' || seed_tmp.rint(0, 255) || '.' || seed_tmp.rint(1, 254), seed_tmp.pick(v_agents), 'BAD_CREDENTIALS',
           u.created_at + random() * (now() - u.created_at)
    FROM users u CROSS JOIN generate_series(1, 3) g WHERE u.provider = 'LOCAL' AND random() < 0.2;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, reason, created_at)
    SELECT 'LOGIN', 'FAILURE', NULL, 'unknown.person' || g || '@example.com', '203.0.113.' || seed_tmp.rint(1, 254), seed_tmp.pick(v_agents),
           'BAD_CREDENTIALS', now() - make_interval(days => seed_tmp.rint(0, 60), hours => seed_tmp.rint(0, 23))
    FROM generate_series(1, 25) g;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, reason, created_at)
    SELECT 'LOGIN', 'FAILURE', u.id, u.email, '198.51.100.23', v_agents[1], 'BAD_CREDENTIALS', now() - make_interval(mins => 30 - g * 5)
    FROM users u CROSS JOIN generate_series(1, 5) g WHERE u.email = 'locked.account@demo.courseaurahub.io';
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, reason, created_at)
    SELECT 'ACCOUNT_LOCKED', 'FAILURE', u.id, u.email, '198.51.100.23', v_agents[1], 'TOO_MANY_FAILED_ATTEMPTS', now() - interval '4 minutes'
    FROM users u WHERE u.email = 'locked.account@demo.courseaurahub.io';
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, reason, created_at)
    SELECT 'ACCOUNT_STATUS_CHANGED', 'SUCCESS', u.id, u.email, '10.0.0.5', v_agents[1], 'DISABLED_BY_ADMIN', now() - interval '30 days'
    FROM users u WHERE NOT u.enabled;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, created_at)
    SELECT x.type, 'SUCCESS', u.id, u.email, '10.0.' || seed_tmp.rint(0, 255) || '.' || seed_tmp.rint(1, 254), seed_tmp.pick(v_agents),
           u.created_at + random() * (now() - u.created_at)
    FROM users u CROSS JOIN (VALUES ('PASSWORD_CHANGED'), ('PASSWORD_RESET_REQUESTED'), ('PASSWORD_RESET_COMPLETED'), ('VERIFICATION_CODE_ISSUED')) AS x(type)
    WHERE u.provider = 'LOCAL' AND u.email_verified AND random() < 0.08;
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, metadata, created_at)
    SELECT 'OAUTH2_ACCOUNT_LINKED', 'SUCCESS', i.user_id, i.email, '10.0.0.' || seed_tmp.rint(1, 254), seed_tmp.pick(v_agents),
           '{"provider":"' || i.provider || '"}', i.created_at
    FROM user_oauth_identities i JOIN users u ON u.id = i.user_id WHERE u.provider = 'LOCAL';
    INSERT INTO auth_audit_events (event_type, outcome, user_id, email, ip_address, user_agent, metadata, created_at)
    SELECT 'OAUTH2_ACCOUNT_CREATED', 'SUCCESS', u.id, u.email, '10.0.0.' || seed_tmp.rint(1, 254), seed_tmp.pick(v_agents),
           '{"provider":"' || u.provider || '"}', u.created_at
    FROM users u WHERE u.provider <> 'LOCAL';
END $seed$;

-- ===============================================================================================================
-- 22. Admin audit log, catalog analytics, scheduled job locks
-- ===============================================================================================================
INSERT INTO admin_audit_events (actor_id, actor_email, actor_role, action, http_method, path_pattern, resource, resource_id,
                                path_variables, status, outcome, request_id, ip_address, created_at)
SELECT a.id, a.email, CASE WHEN a.role = 'ROLE_ADMIN' THEN 'ADMIN' ELSE 'INSTRUCTOR' END, t.action, t.method, t.pattern, t.resource,
       CASE WHEN t.pattern LIKE '%{slug}%' THEN c.slug END,
       CASE WHEN t.pattern LIKE '%{slug}%' THEN '{"slug":"' || c.slug || '"}' END,
       CASE WHEN random() < 0.06 THEN (ARRAY[400, 403, 404, 409])[seed_tmp.rint(1, 4)] ELSE t.status END,
       'SUCCESS', 'req-' || seed_tmp.hex(12), '10.0.0.' || seed_tmp.rint(2, 60),
       now() - make_interval(days => seed_tmp.rint(0, 200), hours => seed_tmp.rint(0, 23), mins => seed_tmp.rint(0, 59))
FROM generate_series(1, 420) g
CROSS JOIN LATERAL (SELECT * FROM (VALUES
    ('admin', 'CourseController.update', 'PUT', '/api/v1/courses/{slug}', 'courses', 200),
    ('admin', 'CourseController.create', 'POST', '/api/v1/courses', 'courses', 201),
    ('admin', 'InstructorController.approve', 'POST', '/api/v1/admin/course-reviews/{slug}/approve', 'course-reviews', 200),
    ('admin', 'InstructorController.reject', 'POST', '/api/v1/admin/course-reviews/{slug}/reject', 'course-reviews', 200),
    ('admin', 'CouponController.create', 'POST', '/api/v1/admin/coupons', 'coupons', 201),
    ('admin', 'CouponController.update', 'PUT', '/api/v1/admin/coupons/{id}', 'coupons', 200),
    ('admin', 'DiscountController.create', 'POST', '/api/v1/admin/discounts', 'discounts', 201),
    ('admin', 'MemberCardController.approve', 'POST', '/api/v1/admin/member-cards/applications/{id}/approve', 'member-cards', 200),
    ('admin', 'MemberCardTopUpController.approve', 'POST', '/api/v1/admin/member-cards/top-ups/{id}/approve', 'member-cards', 200),
    ('admin', 'MemberCardController.adjust', 'POST', '/api/v1/admin/member-cards/{id}/adjustments', 'member-cards', 200),
    ('admin', 'OrderController.refund', 'POST', '/api/v1/admin/orders/{orderNumber}/refund', 'orders', 200),
    ('admin', 'RefundRequestController.approve', 'POST', '/api/v1/admin/refund-requests/{id}/approve', 'refund-requests', 200),
    ('admin', 'PayoutController.approve', 'POST', '/api/v1/admin/payouts/{id}/approve', 'payouts', 200),
    ('admin', 'PayoutController.markPaid', 'POST', '/api/v1/admin/payouts/{id}/paid', 'payouts', 200),
    ('admin', 'StudentController.update', 'PUT', '/api/v1/students/{id}', 'students', 200),
    ('admin', 'SettingsController.update', 'PUT', '/api/v1/settings', 'settings', 200),
    ('admin', 'CategoryController.create', 'POST', '/api/v1/admin/categories', 'categories', 201),
    ('admin', 'ReportController.resolve', 'POST', '/api/v1/admin/reports/{id}/resolve', 'reports', 200),
    ('admin', 'SubscriptionPlanController.update', 'PUT', '/api/v1/admin/subscription-plans/{id}', 'subscription-plans', 200),
    ('instructor', 'InstructorController.create', 'POST', '/api/v1/instructor/courses', 'courses', 201),
    ('instructor', 'InstructorController.update', 'PUT', '/api/v1/instructor/courses/{slug}', 'courses', 200),
    ('instructor', 'InstructorController.submit', 'POST', '/api/v1/instructor/courses/{slug}/submit', 'courses', 200),
    ('instructor', 'AnnouncementController.create', 'POST', '/api/v1/instructor/courses/{slug}/announcements', 'courses', 201),
    ('instructor', 'LiveSessionController.create', 'POST', '/api/v1/instructor/courses/{slug}/live-sessions', 'courses', 201),
    ('instructor', 'SurveyController.save', 'PUT', '/api/v1/instructor/courses/{slug}/survey', 'courses', 200),
    ('instructor', 'AssignmentController.grade', 'POST', '/api/v1/instructor/submissions/{id}/grade', 'submissions', 200),
    ('instructor', 'QuizController.grantAttempts', 'POST', '/api/v1/admin/lessons/{lessonId}/quiz/attempt-grants', 'lessons', 200),
    ('instructor', 'PayoutController.request', 'POST', '/api/v1/instructor/payouts', 'payouts', 201)
) AS t(who, action, method, pattern, resource, status) ORDER BY random() + g * 0 LIMIT 1) t
CROSS JOIN LATERAL (SELECT u.* FROM users u WHERE (t.who = 'admin' AND u.role = 'ROLE_ADMIN') OR (t.who = 'instructor' AND u.role = 'ROLE_INSTRUCTOR')
                    ORDER BY random() + g * 0 LIMIT 1) a
CROSS JOIN LATERAL (SELECT co.slug FROM courses co WHERE (a.role = 'ROLE_ADMIN' OR co.owner_id = a.id) ORDER BY random() + g * 0 LIMIT 1) c;
UPDATE admin_audit_events SET outcome = 'FAILURE' WHERE status >= 400;

INSERT INTO catalog_events (course_id, event, visitor_hash, created_at)
SELECT co.id, ev.event, encode(sha256(('visitor-' || seed_tmp.rint(1, 6000))::bytea), 'hex'),
       now() - make_interval(days => seed_tmp.rint(0, 179), hours => seed_tmp.rint(0, 23), mins => seed_tmp.rint(0, 59))
FROM seed_tmp.catalog c JOIN courses co ON co.id = c.course_id AND co.status = 'PUBLISHED'
CROSS JOIN LATERAL generate_series(1, c.popularity * 140) g
CROSS JOIN LATERAL (SELECT CASE WHEN r < 0.82 THEN 'VIEW' WHEN r < 0.94 THEN 'ADD_TO_CART' ELSE 'CHECKOUT_START' END AS event
                    FROM (SELECT random() + g * 0 AS r) x) ev;

INSERT INTO scheduled_job_locks (name, locked_until, locked_at, locked_by)
SELECT name, now() - interval '1 minute', now() - interval '6 minutes', 'seed'
FROM unnest(ARRAY['auth-maintenance', 'catalog-events-prune', 'weekly-report', 'file-cleanup', 'live-session-reminders',
                  'notification-digest', 'instructor-earnings-release', 'referral-rewards', 'learner-reminders',
                  'subscription-renewals', 'telegram-polling', 'video-transcode']) AS name;

-- ===============================================================================================================
-- 23. Derived values, exactly as the services keep them
-- ===============================================================================================================
UPDATE courses c SET
    total_lessons = t.lessons,
    total_hours = round(t.minutes / 60.0, 2)
FROM (SELECT s.course_id, count(l.id) AS lessons, coalesce(sum(l.duration_minutes), 0) AS minutes
      FROM course_sections s LEFT JOIN lessons l ON l.section_id = s.id GROUP BY s.course_id) t
WHERE t.course_id = c.id;
UPDATE courses c SET
    total_students = (SELECT count(*) FROM student_courses sc WHERE sc.course_id = c.id),
    revenue = coalesce((SELECT sum(amount) FROM enrollments e WHERE e.course_id = c.id), 0),
    rating = coalesce((SELECT round(avg(r.rating), 1) FROM course_reviews r WHERE r.course_id = c.id AND NOT r.hidden), 0),
    completion_rate = coalesce((SELECT round(100.0 * count(*) FILTER (WHERE completed) / nullif(count(*), 0), 2)
                                FROM student_courses sc WHERE sc.course_id = c.id), 0);
UPDATE course_monthly_metrics m SET
    enrollments = coalesce(x.enrollments, 0),
    revenue = coalesce(x.revenue, 0)
FROM (SELECT mm.course_id, mm.month, count(e.id) AS enrollments, sum(e.amount) AS revenue
      FROM course_monthly_metrics mm
      LEFT JOIN enrollments e ON e.course_id = mm.course_id AND extract(month FROM e.enrolled_at) = mm.month
      GROUP BY mm.course_id, mm.month) x
WHERE x.course_id = m.course_id AND x.month = m.month;
UPDATE students s SET
    total_spent = coalesce((SELECT sum(amount) FROM enrollments e WHERE e.student_id = s.id), 0),
    last_active = greatest(s.joined_at,
                           coalesce((SELECT max(lp.completed_at)::date FROM lesson_progress lp WHERE lp.student_id = s.id), s.joined_at),
                           coalesce((SELECT max(e.enrolled_at) FROM enrollments e WHERE e.student_id = s.id), s.joined_at));
UPDATE course_reviews r SET helpful_count = (SELECT count(*) FROM review_votes v WHERE v.review_id = r.id);
UPDATE access_codes a SET uses = (SELECT count(*) FROM access_code_redemptions r WHERE r.code_id = a.id);
UPDATE order_groups g SET amount = coalesce((SELECT sum(amount) FROM orders o WHERE o.group_id = g.id), 0);
DELETE FROM order_groups g WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.group_id = g.id);
UPDATE users u SET updated_at = greatest(u.updated_at, coalesce((SELECT max(created_at) FROM refresh_sessions rs WHERE rs.user_id = u.id), u.updated_at));

DROP SCHEMA seed_tmp CASCADE;
