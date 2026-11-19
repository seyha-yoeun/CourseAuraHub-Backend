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
| `course` | Course catalog (admin CRUD, public read), scheduled sale prices | `/api/v1/courses`, `/api/v1/admin/discounts` (ADMIN) |
| `student` | Admin student management | `/api/v1/students` (ADMIN) |
| `enrollment` | Admin enrollments; keeps counters + monthly metrics in sync | `/api/v1/enrollments` (ADMIN) |
| `dashboard` | Admin stats | `/api/v1/stats` (ADMIN) |
| `settings` | Platform settings (single row) | `/api/v1/settings` |
| `learner` | Self-service: my courses, self-enroll, dashboard, achievements, profile, password, wishlist | `/api/v1/me` |
| `certificate` | Completion certificates, PDF download, public verification | `/api/v1/me/certificates`, `/api/v1/me/certificates/{number}/pdf`, `/api/v1/certificates/{number}` (public), Open Badges `/api/v1/certificates/{number}/badge.json` and `/api/v1/badges/**` (public), `/api/v1/me/certificates/{number}/linkedin-url` |
| `curriculum` | Sections, lessons, lesson progress, video resume | `/api/v1/courses/{slug}/curriculum`, `/api/v1/me/lessons` |
| `order` | Checkout, order history, refunds, PDF receipts, `PaymentGateway` port | `/api/v1/me/orders`, `/api/v1/me/orders/{number}/receipt`, `/api/v1/admin/orders`, `/api/v1/admin/orders/{number}/receipt`, `/api/v1/payments/webhooks/{provider}` |
| `coupon` | Discount codes; redemptions are derived from orders | `/api/v1/me/coupons/preview`, `/api/v1/admin/coupons` |
| `notification` | Saved notifications and the live SSE stream | `/api/v1/me/notifications`, `/api/v1/me/notifications/stream` |
| `telegram` | Connects accounts to private chats, sends notifications there, bot commands | `/api/v1/me/telegram`, `/api/v1/telegram/webhook` |
| `cart` | Cart and multi-course checkout | `/api/v1/me/cart` |
| `video` | HLS transcoding of lesson videos (FFmpeg, off by default), signed playlists, WebVTT captions and transcripts | `/api/v1/me/lessons/{id}/media`, `/api/v1/me/lessons/{id}/stream.m3u8`, `/api/v1/me/lessons/{id}/transcript`, `/api/v1/courses/{slug}/lessons/{id}/captions/{lang}` |
| `quiz` | Graded quizzes for QUIZ lessons | `/api/v1/me/lessons/{id}/quiz`, `/api/v1/admin/lessons/{id}/quiz` (ADMIN) |
| `coding` | CODE lessons with coding exercises, run in a Judge0 sandbox and graded against hidden tests | `/api/v1/instructor/lessons/{id}/exercise`, `/api/v1/me/lessons/{id}/exercise`, `/api/v1/me/lessons/{id}/exercise/runs` |
| `review` | Course reviews and ratings | `/api/v1/courses/{slug}/reviews`, `/api/v1/me/courses/{slug}/review` |
| `discussion` | Lesson questions and answers, staff inbox | `/api/v1/me/lessons/{id}/questions`, `/api/v1/me/questions`, `/api/v1/instructor/questions` |
| `note` | Private lesson notes and video bookmarks | `/api/v1/me/lessons/{id}/notes`, `/api/v1/me/notes` |
| `category` | Managed course categories | `/api/v1/categories` (public), `/api/v1/admin/categories` |
| `assistant` | AI study assistant (Anthropic API, off without a key): streamed answers from a lesson's study text, summaries, quiz drafts | `/api/v1/me/lessons/{id}/assistant/messages` (SSE), `/api/v1/me/lessons/{id}/summary`, `/api/v1/instructor/lessons/{id}/assistant-source`, `/api/v1/instructor/lessons/{id}/quiz/draft` |
| `assignment` | Assignments, submissions and grading | `/api/v1/me/lessons/{id}/assignment`, `/api/v1/instructor/lessons/{id}/assignment`, `/api/v1/instructor/submissions` |
| `instructor` | An instructor's own courses, and the admin review that publishes them | `/api/v1/instructor`, `/api/v1/admin/course-reviews` |
| `reminder` | Nudges to return to an unfinished course | `/api/v1/me/reminders` |
| `payout` | Instructor earnings from sales of courses they own, payout requests and admin review, per-course revenue share | `/api/v1/instructor/earnings`, `/api/v1/instructor/payouts`, `/api/v1/admin/payouts`, `/api/v1/admin/courses/{slug}/revenue-share` |
| `privacy` | Personal data export and self-service account deletion | `/api/v1/me/data-export`, `/api/v1/me/account/deletion` |
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
- **Certificates are permanent**: issue them through `CertificateService` wherever `student_courses.completed` can flip to true (lesson completion and curriculum edits). They are never revoked when a course re-opens. Name, title and instructor are copied onto the row at issue, so read those columns, not `student` or `course`. PDFs (`CertificatePdfRenderer`) shape Khmer with Java2D and draw it as outlines (`CertificateTextOutliner`), because PDFBox can't shape it. That needs fontconfig and freetype in the image. Other scripts missing from the fonts, such as Chinese, get a clear error and print the web page instead.
- **Achievements and streaks are derived, not stored** (`LearnerStatsService`, `Achievement` enum). Streak days come from `lesson_progress.completed_at` in the caller's `timeZone`, so un-completing a lesson also removes that day's activity.
- **Payments**: enrollment for paid courses happens only in `OrderService.applyPaymentEvent`, after the gateway verifies the webhook. Keep provider calls outside transactions (`CheckoutFacade`). The `fake` gateway lets anyone with the webhook secret mark orders paid; never enable it in production.
- **Sale prices**: `courses.price` is always the regular price and a sale is never written to it. Use `Course.currentPrice()` for anything a learner sees or pays. Admin course responses keep the regular price, because the edit form saves it back. Catalog sorting by price uses the regular price.
- **Cart**: each course keeps its own `Order`, and an `OrderGroup` ties them to one payment. Webhooks may carry a `GRP-` number instead of an order number. Grouped orders are cancelled together, and a coupon applies to one course in the cart (`CouponService.chooseCourse`).
- **Coding exercises**: learners' code only ever runs in the external sandbox behind `CodeRunner` (Judge0), never in this JVM, and with no transaction open. Hidden tests' inputs, expected outputs and the program's output or stderr on them are never returned, because a program could print its input. A CODE lesson with an exercise can only be completed by passing it (`LessonProgressServiceImpl.requirePassedExercise`).
- **Quizzes**: answers are graded on the server and correct options never reach learners before they pass. A QUIZ lesson with questions can only be completed by passing it; one without questions behaves like any other lesson.
- **Coupons**: price orders only through `CouponService.applyToOrder`, which locks the coupon inside the order's transaction. `Order.amount` is what the learner pays, `listAmount` the price before discount. Orders that reach zero settle without a provider.
- **Notifications**: call `NotificationService` inside the business transaction, so the row commits or rolls back with the change. Pushes happen after commit on the dispatcher's own threads. Open streams are held in memory per instance, so a push only reaches clients on the same server; the REST feed is always complete.
- **File uploads**: browsers PUT straight to storage with a presigned URL (`FileService`), into `uploads/`. `completeUpload` checks the size and first bytes before moving a file to `public/` or `private/`, so never presign a final key. Lesson files stay private: `lessons.content_url` holds a `storage://` reference and `CurriculumAssembler` signs it only for unlocked lessons. Never write a signed URL to the database; route lesson URLs through `FileService.toStoredUrl`. SVG stays disallowed because it can run scripts. Keep storage calls outside transactions.
- **Video streams**: `VideoTranscodeJob` finds VIDEO lessons whose `content_url` differs from `video_source_ref` and transcodes them with FFmpeg into `private/hls/{lessonId}/{jobId}/`, outside transactions. The playlist stays private: `VideoServiceImpl.playlist` signs every segment per request, so only viewers who can watch the lesson get them. Caption files must come from the caller's own `LESSON_CAPTION` upload (`FileService.requireOwned`).
- **Member cards**: change a balance only through `MemberCardServiceImpl.record`, under a lock on the card row, so every change has a ledger row. Keep `charge` and `settle` in one transaction (`OrderService.payWithMemberCard`), with the learner row locked first so parallel checkouts can't both charge. Orders paid this way have provider `member_card` and refund to the card without a gateway. Admins can't approve, create, change, adjust or delete their own card. Only a card with no ledger rows can be deleted; a used card is suspended, because the ledger is the money record.
- **Telegram**: `TelegramNotifier` listens to `NotificationsCreated`, so anything sent through `NotificationService` reaches connected chats without extra code. Bot replies must only use the account connected to the chat, and every dynamic value must go through `TelegramMessages.escape`. The bot token is part of Telegram URLs, so never log a Telegram client exception's cause.
- **Never expose learner PII** (email, total spent) to non-admins. The `enrolledStudentList` field on course detail is admin-only.
- **Migrations**: check the highest `V####` in `src/main/resources/db/migration/` before adding one. `V0013`, `V0017` and `V0036` seed sample data. Flyway runs each once, so add new seed data in a new migration and never edit an applied one.
- **Study assistant**: only `ClaudeGateway` calls the Anthropic SDK, on the beta endpoint with server-side refusal fallbacks (`fallbacksDefault()`), and never inside a transaction. The system prompt holds the lesson's whole study text (`lesson_ai_sources`) and is prompt-cached, so keep it stable per lesson; it must never contain learner names, emails or quiz answers. Answers stream on the service's own executor; usage counts toward `app.assistant.daily-token-limit`.
- **Tests**: `IntegrationTestBase` is shared by every module but lives in `src/test/java/com/course_aura_hub/auth/`. It turns rate limiting off.

- **Course access**: decide who manages a course with `CourseAccess` (`@courseAccess` in `@PreAuthorize`), not `isAdmin`: instructors manage only courses with their `owner_id`. Courses without an owner are admin-managed. Only admin approval publishes an instructor's course, so never let the instructor routes set a status. Instructors must never see learner emails or spending.
- **Private file references**: any endpoint that stores a `storage://` value from a caller must call `FileService.requireOwned` first (`CourseAccess.requireUsableFile` for lessons). Stored references get signed links, so skipping this lets someone read another person's private file.
- **Top-up requests**: a request moves no money. Approval credits the card through `MemberCardService.creditTopUp`, with `TOPUP-{id}` as the ledger reference.
- **Required assignments**: a lesson with a required assignment can only be completed by a passing grade, which `AssignmentService.grade` applies through `LessonProgressService.completeForStudent`.
- **Instructor earnings** come from `OrderPaid` and `OrderRefunded`, which `OrderServiceImpl` publishes inside the payment and refund transactions; `EarningServiceImpl` listens to them. Only courses with an `owner_id` earn. A refund before the money is claimed reverses the sale; after that it adds a negative `REFUND` row that comes off the next payout, so never edit a claimed or paid earning. A payout claims every available row under a row lock, and only one payout per instructor can be open.
- **Account deletion** (`AccountPrivacyServiceImpl`) anonymizes instead of deleting rows: orders, member card ledgers and certificates are kept under "Deleted learner". When you add a table holding personal data, add it to both the export queries and the deletion steps.
- **Logging**: the console stays human-readable; `logs/course-aura-hub.log` is Logstash JSON and Alloy ships it to Loki (`docs/logging.md`). `RequestLoggingFilter` logs method, path, status and duration and sets the `requestId` MDC value. Never add query strings, headers, cookies or bodies to it. `docker compose down -v` also deletes Postgres data.

## Roadmap (not yet built)

1. A real payment provider behind `PaymentGateway`; only the dev-only `fake` gateway exists, so paid checkout is off by default.
2. Public instructor profile pages.
3. Recommendations by tags and category.
