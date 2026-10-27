# Course Aura Hub — Backend

Course Aura Hub is an online learning platform. Learners browse a course catalog, enroll, work through lessons while their progress is tracked, and review the courses they take. Administrators run the platform from a dashboard: they publish courses, build each course's curriculum, manage students and enrollments, moderate reviews and follow sales and enrollment analytics.

This repository is the **REST API** behind the platform. The web app lives in the companion repository [`CourseAuraHub-Frontend`](../CourseAuraHub-Frontend).

## What it does

**For learners** (`/api/v1/me/**`)
- Sign up with email and password, or sign in with Google, GitHub or Facebook
- Browse published courses and view their curriculum, with free-preview lessons
- Enroll in free courses (paid checkout is on the roadmap)
- Mark lessons complete; course progress and completion are tracked automatically
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

# 2. Start PostgreSQL (port 5432) and pgAdmin (port 5050)
docker compose up -d

# 3. Run the API on http://localhost:8080
./mvnw spring-boot:run
```

Flyway creates the schema and seeds sample data on first start: about a year of mock courses, students and enrollments, plus a free starter course (**Git & GitHub Fundamentals**) with a full curriculum.

**Creating an admin account:** set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` in `.env` before starting the app. To promote accounts that already exist, list their emails in `BOOTSTRAP_ADMIN_PROMOTE_EMAILS`.

**Useful URLs**
- Swagger UI: http://localhost:8080/swagger-ui.html
- pgAdmin: http://localhost:5050
- Postman collection: [`postman/`](postman/README.md)

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
| `/api/v1/me/**` | Signed-in user | My courses, enrollment, lesson progress, wishlist, my review, profile, password |
| `/api/v1/students`, `/enrollments`, `/stats`, `/settings` | Admin | Platform management |

See Swagger UI for every endpoint, its request body and its responses.

## Project structure

Code is organized by feature module under `src/main/java/com/course_aura_hub/`. Each module uses the same layers: `controller → service → repository`, plus `dto`, `model` and `mapper`.

```
auth/         users, JWT cookies, OAuth2, lockout, rate limiting, SecurityConfig
course/       course catalog and monthly metrics
curriculum/   sections, lessons, lesson progress
learner/      learner self-service: my courses, enroll, profile, wishlist
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
2. Completion certificates with a public verification URL
3. Password reset and email verification
4. Public catalog for visitors who aren't signed in
5. Notifications feed
6. Lesson Q&A, discussions and personal notes
7. Categories and instructors as first-class entities
8. Learning streaks, "continue learning" and recommendations
