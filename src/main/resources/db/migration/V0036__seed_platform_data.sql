-- V0036: Seeds the features added after V0017 so a new install has data to show: quizzes, coupons, a sale,
-- past orders and reviews. Flyway runs this once and records it, so it never seeds again, and deleting
-- this data later won't bring it back. Each insert also skips rows that already exist. Orders and reviews
-- only use the sample students, who have no account, so real learners are never given invented history.

DO $seed$
DECLARE
    v_quiz jsonb;
    v_question jsonb;
    v_lesson_id integer;
    v_question_id integer;
    v_position integer;
BEGIN
    FOR v_quiz IN SELECT * FROM jsonb_array_elements($quizzes$[
        {
            "slug": "git-github-fundamentals",
            "lesson": "Check your knowledge",
            "questions": [
                {
                    "prompt": "What does a commit record?",
                    "explanation": "A commit is a snapshot of your tracked files at a point in time.",
                    "options": ["A snapshot of your project", "A copy of the remote server", "A list of collaborators", "A new branch"],
                    "correct": 0
                },
                {
                    "prompt": "Which command creates a new branch and switches to it?",
                    "explanation": "git switch -c creates the branch and moves you onto it in one step.",
                    "options": ["git commit -b", "git switch -c", "git push -b", "git log -c"],
                    "correct": 1
                },
                {
                    "prompt": "What is a pull request for?",
                    "explanation": "A pull request asks teammates to review changes before they are merged.",
                    "options": ["Deleting a repository", "Downloading a repository", "Proposing changes for review", "Undoing the last commit"],
                    "correct": 2
                },
                {
                    "prompt": "Which command sends your commits to the remote repository?",
                    "explanation": "git push uploads your local commits to the remote.",
                    "options": ["git pull", "git status", "git add", "git push"],
                    "correct": 3
                }
            ]
        },
        {
            "slug": "nuxt-3-mastery",
            "lesson": "Section quiz",
            "questions": [
                {
                    "prompt": "What is Pinia used for in a Vue app?",
                    "explanation": "Pinia is the official state management library for Vue.",
                    "options": ["Sharing application state", "Routing between pages", "Compiling templates", "Writing unit tests"],
                    "correct": 0
                },
                {
                    "prompt": "Which composable fetches data in a Nuxt page with server-side rendering support?",
                    "explanation": "useFetch runs on the server and avoids fetching the same data again in the browser.",
                    "options": ["useRouter", "useFetch", "useHead", "useState"],
                    "correct": 1
                },
                {
                    "prompt": "What does server-side rendering send to the browser first?",
                    "explanation": "The server renders HTML, which the browser then makes interactive through hydration.",
                    "options": ["Only JavaScript", "An empty page", "Rendered HTML", "A database dump"],
                    "correct": 2
                }
            ]
        }
    ]$quizzes$::jsonb)
    LOOP
        SELECT l.id INTO v_lesson_id
        FROM lessons l
        JOIN course_sections s ON s.id = l.section_id
        JOIN courses c ON c.id = s.course_id
        WHERE c.slug = v_quiz ->> 'slug' AND l.title = v_quiz ->> 'lesson' AND l.type = 'QUIZ';

        IF v_lesson_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM quiz_questions WHERE lesson_id = v_lesson_id) THEN
            v_position := 0;
            FOR v_question IN SELECT * FROM jsonb_array_elements(v_quiz -> 'questions')
            LOOP
                INSERT INTO quiz_questions (lesson_id, position, prompt, explanation)
                VALUES (v_lesson_id, v_position, v_question ->> 'prompt', v_question ->> 'explanation')
                RETURNING id INTO v_question_id;

                INSERT INTO quiz_options (question_id, position, text, correct)
                SELECT v_question_id,
                       option.ordinality - 1,
                       option.value,
                       option.ordinality - 1 = (v_question ->> 'correct')::integer
                FROM jsonb_array_elements_text(v_question -> 'options') WITH ORDINALITY AS option;

                v_position := v_position + 1;
            END LOOP;
        END IF;
    END LOOP;
END
$seed$;

INSERT INTO coupons (code, discount_type, discount_value, course_id)
VALUES ('WELCOME10', 'PERCENT', 10, NULL),
       ('SAVE5', 'FIXED', 5, NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO coupons (code, discount_type, discount_value, course_id)
SELECT 'NUXT25', 'PERCENT', 25, id FROM courses WHERE slug = 'nuxt-3-mastery'
ON CONFLICT (code) DO NOTHING;

INSERT INTO course_discounts (course_id, discount_type, discount_value, starts_at, ends_at)
SELECT c.id, 'PERCENT', 20, now(), now() + interval '60 days'
FROM courses c
WHERE c.slug = 'spring-boot-microservices'
  AND NOT EXISTS (SELECT 1 FROM course_discounts d WHERE d.course_id = c.id);

-- The order history behind the sample students' paid enrollments
INSERT INTO orders (order_number, student_id, course_id, list_amount, discount_amount, amount, currency, status, provider, created_at, paid_at)
SELECT 'SEED-' || lpad(e.id::text, 6, '0'),
       e.student_id,
       e.course_id,
       e.amount,
       0,
       e.amount,
       'USD',
       'PAID',
       'seed',
       e.enrolled_at::timestamptz,
       e.enrolled_at::timestamptz
FROM enrollments e
JOIN students s ON s.id = e.student_id
WHERE e.amount > 0
  AND s.user_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM orders o
      WHERE o.student_id = e.student_id AND o.course_id = e.course_id AND o.status = 'PAID'
  )
ON CONFLICT (order_number) DO NOTHING;

-- Up to three reviews per course from its sample students
INSERT INTO course_reviews (course_id, student_id, rating, comment, created_at, updated_at)
SELECT ranked.course_id,
       ranked.student_id,
       CASE ranked.position WHEN 1 THEN 5 WHEN 2 THEN 4 ELSE 5 END,
       CASE ranked.position
           WHEN 1 THEN 'Clear explanations and practical examples. I used what I learned at work the same week.'
           WHEN 2 THEN 'Well structured and easy to follow. A few more exercises would make it even better.'
           ELSE 'Great pace from start to finish. Exactly what I needed to level up.'
       END,
       ranked.enrolled_at::timestamptz,
       ranked.enrolled_at::timestamptz
FROM (
    SELECT e.course_id,
           e.student_id,
           e.enrolled_at,
           row_number() OVER (PARTITION BY e.course_id ORDER BY e.id) AS position
    FROM enrollments e
    JOIN students s ON s.id = e.student_id
    JOIN courses c ON c.id = e.course_id
    WHERE s.user_id IS NULL
      AND c.slug NOT IN ('git-github-fundamentals', 'nuxt-3-mastery')
) ranked
WHERE ranked.position <= 3
ON CONFLICT (course_id, student_id) DO NOTHING;
