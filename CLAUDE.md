@AGENTS.md

# CLAUDE.md - Course Aura Hub Backend

`AGENTS.md` (imported above) holds the architecture and coding conventions. This file adds commands, a module map, project-specific gotchas and the roadmap.

## Commands

Run build, test, or git commands only when the user asks (see `AGENTS.md`).

```bash
docker compose up -d                  # Postgres 16 (5432) + pgAdmin (5050)
./mvnw spring-boot:run                # run the API
./mvnw test                           # all tests (needs Docker for Testcontainers)
./mvnw test -Dtest=ClassName          # one test class
```

- Swagger UI: `/swagger-ui.html`
- Demo data: set `DEMO_DATA_ENABLED=true` to import `src/main/resources/demo/demo-data.json` on startup.
- Config: `src/main/resources/application.yml` only (no Spring profiles). Secrets come from env vars; `.env.example` lists them. Never read `.env`.

## Module Map (`src/main/java/com/course_aura_hub/`)

| Module | Purpose | Base path |
|---|---|---|
| `auth` | Users, JWT cookies, OAuth2, lockout, rate limiting, `SecurityConfig` | `/api/v1/auth` |
| `course` | Course catalog (admin CRUD, authenticated read) | `/api/v1/courses` |
| `student` | Admin student management | `/api/v1/students` (ADMIN) |
| `enrollment` | Admin enrollments; keeps counters + monthly metrics in sync | `/api/v1/enrollments` (ADMIN) |
| `dashboard` | Admin stats | `/api/v1/stats` (ADMIN) |
| `settings` | Platform settings (single row) | `/api/v1/settings` |
| `learner` | Self-service: my courses, self-enroll, dashboard, achievements, profile, password, wishlist | `/api/v1/me` |
| `certificate` | Completion certificates, public verification | `/api/v1/me/certificates`, `/api/v1/certificates/{number}` (public) |
| `curriculum` | Sections, lessons, lesson progress | `/api/v1/courses/{slug}/curriculum`, `/api/v1/me/lessons` |
| `order` | Checkout, order history, refunds, `PaymentGateway` port | `/api/v1/me/orders`, `/api/v1/admin/orders`, `/api/v1/payments/webhooks/{provider}` |
| `review` | Course reviews and ratings | `/api/v1/courses/{slug}/reviews`, `/api/v1/me/courses/{slug}/review` |
| `common` | `ApiResponse`, shared exceptions + `DomainExceptionHandler`, `SecurityUtils` | — |
| `config` | Demo data seeder, domain config properties | — |

## Gotchas

- **Security matcher order matters** (`auth/config/SecurityConfig.java`). `POST/PUT/PATCH/DELETE /api/v1/courses/**` are ADMIN-only, so learner write endpoints live under `/api/v1/me/**`. Admin-only curriculum and review moderation endpoints sit under `/api/v1/courses/**` so they inherit that rule.
- **User vs Student**: `users` (UUID) is the auth identity. `students` (int id) owns enrollments, progress, reviews and wishlist. They are linked by `students.user_id`. Resolve the current learner through `LearnerAccountService.currentStudent(email)`, which lazily creates and links the student row.
- **JWT principal** is a Spring `UserDetails` whose username is the user's email.
- **Denormalized counters** are maintained in the service layer. Update them whenever related data changes:
  - `courses`: `total_students`, `revenue`, `rating`, `total_lessons`, `total_hours`
  - `course_monthly_metrics`
  - `students.total_spent`
  - Reuse `EnrollmentService.create/delete` instead of re-implementing enrollment side effects.
- **Certificates are permanent**: issue them through `CertificateService` wherever `student_courses.completed` can flip to true (lesson completion and curriculum edits). They are never revoked when a course re-opens.
- **Achievements and streaks are derived, not stored** (`LearnerStatsService`, `Achievement` enum). Streak days come from `lesson_progress.completed_at` in the caller's `timeZone`, so un-completing a lesson also removes that day's activity.
- **Payments**: enrollment for paid courses happens only in `OrderService.applyPaymentEvent`, after the gateway verifies the webhook. Keep provider calls outside transactions (`CheckoutFacade`). The `fake` gateway lets anyone with the webhook secret mark orders paid; never enable it in production.
- **Never expose learner PII** (email, total spent) to non-admins. The `enrolledStudentList` field on course detail is admin-only.
- **Migrations**: check the highest `V####` in `src/main/resources/db/migration/` before adding one. `V0013` is a large mock-data seed.
- **Tests**: `IntegrationTestBase` is shared by every module but lives in `src/test/java/com/course_aura_hub/auth/`. It turns rate limiting off.

## Roadmap (not yet built)

1. A real payment provider behind `PaymentGateway`; only the dev-only `fake` gateway exists, so paid checkout is off by default.
2. Public anonymous catalog (`permitAll` on GET for courses, curriculum outline and reviews).
3. Notifications feed (enrollment, new lesson, review reply).
4. Lesson Q&A, discussions and personal notes.
5. Categories and instructors as real entities, plus instructor profile pages.
6. Recommendations by tags and category.
