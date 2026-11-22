# Course Aura Hub — Backend

Course Aura Hub is an online learning platform. Learners browse a course catalog, enroll, work through lessons while their progress is tracked, and review the courses they take. Administrators run the platform from a dashboard: they publish courses, build each course's curriculum, manage students and enrollments, moderate reviews and follow sales and enrollment analytics.

This repository is the **REST API** behind the platform. The web app lives in the companion repository [`CourseAuraHub-Frontend`](../CourseAuraHub-Frontend).

## What it does

**For learners** (`/api/v1/me/**`)
- Sign up with email and password, or sign in with Google, GitHub or Facebook
- Browse published courses and view their curriculum, with free-preview lessons
- Enroll in free courses (paid checkout is on the roadmap)
- Mark lessons complete; course progress and completion are tracked automatically
- Pick up where they left off: every enrolled course points to its next unfinished lesson
- A learning dashboard with time learned, daily streaks, the last week's activity and a course to continue
- Earn a completion certificate per course, verifiable by anyone at a public URL, plus achievement badges
- Keep a wishlist, rate and review enrolled courses, and edit their profile and password

**For administrators**
- Create, edit, publish and archive courses
- Build curricula: sections and lessons (video, article, quiz) with ordering and free previews
- Manage students and enrollments, and moderate reviews
- Dashboard stats: revenue, enrollments, top courses, monthly trends and revenue by category
- Platform settings: branding, currency, language and notification preferences

**Security**
- JWT access and refresh tokens in HttpOnly cookies, with refresh-token rotation
- OAuth2 social login
- CSRF double-submit protection
- Account lockout after repeated failed logins, plus rate limiting
- Role-based access (`ROLE_USER`, `ROLE_ADMIN`)

## Tech stack

| Area | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 4.1 |
| Database | PostgreSQL 16, Flyway migrations |
| Security | Spring Security, JJWT, OAuth2 Client |
| Mapping / boilerplate | MapStruct, Lombok |
| API docs | Springdoc OpenAPI (Swagger UI) |
| Rate limiting | Bucket4j |
| Testing | JUnit 5, MockMvc, Testcontainers |

## Getting started

**Prerequisites:** Java 21 and Docker (for PostgreSQL, and for running the tests).

```bash
# 1. Configure the environment (the app reads .env from the project root)
cp .env.example .env
#    then set JWT_SECRET, plus OAuth client IDs if you want social login

# 2. Start PostgreSQL (port 5432), pgAdmin (port 5050), Mailpit (port 8025) and MinIO (API 9000, console 9001)
#    Compose files live in infrastructure/; --env-file passes the root .env, which Compose would otherwise not read
docker compose -f infrastructure/docker-compose.yaml --env-file .env up -d

# 3. Run the API on http://localhost:8080
./mvnw spring-boot:run
```

Flyway creates the schema and seeds sample data on first start: about a year of mock courses, students and enrollments, plus a free starter course (**Git & GitHub Fundamentals**) with a full curriculum.

**Demo data for the whole app (local only):** set `DEMO_DATA_ENABLED=true` (and optionally `DEMO_PASSWORD`, default `Demo@2026!`) and restart. On its first run against a local database it **empties every table** (including the migration sample data) and fills all of them from `src/main/resources/demo/seed.sql`: 9 instructors, about 200 learners, 36 courses with around 600 lessons, orders in every status, subscriptions, member cards, gifts, organizations, live classes, surveys, notifications and more. It runs once per database (`demo_seed_history`), and refuses any host not in `DEMO_DATA_LOCAL_HOSTS`. Every video lesson plays the same YouTube video. To reseed, drop the database and start again. LOCAL accounts all sign in with that password:

| Account | Role | What you'll see |
|---|---|---|
| `admin@demo.courseaurahub.io` | Admin | Courses waiting for review, card applications and top-ups, refund requests, payouts to approve, reports, the audit log |
| `support@demo.courseaurahub.io` | Admin | A second admin (also teaches Public Speaking) |
| `sokha.chan@demo.courseaurahub.io` | Instructor | Python for Data Analysis (sequential, drip, timed quiz, required assignment, coding exercises), SQL, data viz, earnings and payouts |
| `lina.meas@`, `dara.pich@`, `visal.tan@`, `maly.nguon@`, `ratanak.seng@`, `sreyneang.prak@`, `kimheng.long@`, `sophal.keo@` | Instructor | Their own courses, Q&A inbox, submissions, live classes, surveys, announcements, payouts |
| `vannak.kim@demo.courseaurahub.io` | Learner | Four courses (one finished), member card with history and a pending top-up, cart, wishlist, Q&A, referral code `VANNAK26`, calendar feed |
| `sreymom.chea@demo.courseaurahub.io` | Learner | Five finished courses, certificates, the Frontend Developer path certificate, reviews |
| `rithy.pov@demo.courseaurahub.io` | Learner | Out of quiz attempts (one granted back), failed and cancelled payments, a pending card application |
| `nita.ouk@demo.courseaurahub.io` | Learner | A refund, a cart checkout awaiting payment, a pending refund request, a rejected card application |
| `bopha.lim@demo.courseaurahub.io` | Learner | A brand-new account with a wishlist; gift code `DEMO-GIFT-2026-0001` is addressed to her |
| `kosal.heng@demo.courseaurahub.io` | Learner | Yearly all-access subscription with courses enrolled through it |
| `malis.tep@demo.courseaurahub.io` | Learner | Owner of the Angkor Tech Solutions organization (seats, members, invites) |
| `piseth.ros@demo.courseaurahub.io` | Learner | Bought gifts for other learners |
| `sovann.chhay@demo.courseaurahub.io` | Learner | Two-factor sign-in (needs `MFA_ENCRYPTION_KEY`): TOTP secret `JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP`, recovery codes `demo-rc01-2026` to `demo-rc09-2026` |
| `chantha.mao@demo.courseaurahub.io` | Learner | Password account with a linked GitHub identity |
| `firstname.lastname@demo.courseaurahub.io` | Learner | The bulk learners, for example `sophea.sok@`, `dara.yim@`, `vuthy.ros@` |

`pending.signup@…` never verified its email, `disabled.account@…` is disabled and `locked.account@…` is locked after failed attempts, so none can sign in. A few bulk accounts were created through Google, GitHub or Facebook and have no password. Batch access code `CAMP-2026-0000-0001` (Docker & Kubernetes) and the calendar feed `/api/v1/calendar/vannak-calendar-demo-token-2026.ics` work too. Never enable demo data in production.

**Creating an admin account:** set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` in `.env` before starting the app. To promote accounts that already exist, list their emails in `BOOTSTRAP_ADMIN_PROMOTE_EMAILS`.

**Useful URLs**
- Swagger UI: http://localhost:8080/swagger-ui.html
- pgAdmin: http://localhost:5050
- Postman collection: [`infrastructure/postman/`](infrastructure/postman/README.md)

## Logs

The backend writes JSON logs to `logs/course-aura-hub.log`. To search them in Grafana at http://localhost:3001:

```bash
docker compose -f infrastructure/docker-compose.yaml --env-file .env --profile logging up -d
```

See [docs/logging.md](docs/logging.md) for ports, queries, retention and troubleshooting.

## Configuration

All configuration lives in `src/main/resources/application.yml` and reads its values from environment variables. [`.env.example`](.env.example) lists every variable. The main groups are:

| Group | Variables |
|---|---|
| Database | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` |
| Tokens | `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRATION`, `JWT_REFRESH_TOKEN_EXPIRATION` |
| OAuth2 | `GOOGLE_*`, `GITHUB_*`, `FACEBOOK_*`, `OAUTH2_REDIRECT_URI` |
| Cookies | `COOKIE_SECURE`, `COOKIE_SAME_SITE`, `COOKIE_DOMAIN`, … |
| Bootstrap | `BOOTSTRAP_ADMIN_*`, `DEMO_DATA_ENABLED` |

Never commit `.env`.

## API overview

All responses use the same envelope: `{ success, message, data, errors, timestamp }`.

| Base path | Access | Purpose |
|---|---|---|
| `/api/v1/auth` | Public / any user | Register, login, refresh, logout, current user, CSRF token |
| `/api/v1/courses` | Read: any user · Write: admin | Course catalog |
| `/api/v1/courses/{slug}/curriculum` | Any user | Sections and lessons (content URLs only for enrolled learners, previews and admins) |
| `/api/v1/courses/{slug}/reviews` | Read: any user · Delete: admin | Course reviews |
| `/api/v1/me/**` | Signed-in user | My courses, enrollment, lesson progress, dashboard, achievements, certificates, wishlist, my review, profile, password |
| `/api/v1/certificates/{number}` | Public | Verify a completion certificate |
| `/api/v1/students`, `/enrollments`, `/stats`, `/settings` | Admin | Platform management |

See Swagger UI for every endpoint, its request body and its responses.

## Project structure

Code is organized by feature module under `src/main/java/com/course_aura_hub/`. Each module uses the same layers: `controller → service → repository`, plus `dto`, `model` and `mapper`.

```
auth/         users, JWT cookies, OAuth2, lockout, rate limiting, SecurityConfig
course/       course catalog and monthly metrics
curriculum/   sections, lessons, lesson progress
learner/      learner self-service: my courses, enroll, dashboard, achievements, profile, wishlist
certificate/  course completion certificates and public verification
review/       ratings and reviews
student/      admin student management
enrollment/   admin enrollments (keeps counters and metrics in sync)
dashboard/    admin statistics
settings/     platform settings
common/       ApiResponse envelope, shared exceptions, security helpers
config/       demo data seeder, domain configuration
```

`AGENTS.md` and `CLAUDE.md` record the coding conventions and project-specific gotchas.

## Testing

```bash
./mvnw test                     # all tests (Docker must be running for Testcontainers)
./mvnw test -Dtest=ClassName    # a single test class
```

Integration tests start a throwaway PostgreSQL container, so they never touch your local database.

## Roadmap

1. Checkout and payments for paid courses, with order history
2. Public catalog for visitors who aren't signed in
3. Notifications feed
4. Lesson Q&A, discussions and personal notes
5. Categories and instructors as first-class entities
6. Course recommendations
