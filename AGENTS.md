# AGENTS.md - Course Aura Hub Backend

Guidelines for AI agents working on this Spring Boot backend.

## Project Stack

- **Framework**: Spring Boot 4.1.1 | **Language**: Java 21 | **Build**: Maven
- **Database**: PostgreSQL + Flyway migrations (`src/main/resources/db/migration/`)
- **Auth**: JWT (JJWT) + Spring Security + OAuth2 (Google, GitHub, Facebook)
- **API Docs**: Springdoc OpenAPI (Swagger UI)
- **Testing**: JUnit 5, Testcontainers (PostgreSQL), MockMvc
- **Mapping**: MapStruct 1.6.3 + Lombok
- **Rate Limiting**: Bucket4j

## Architecture (All Modules Follow This)

```
com.course_aura_hub
├── {module}
│   ├── controller     # @RestController, HTTP concerns only
│   ├── service        # Interfaces + @Service impls, pure business logic
│   ├── repository     # JpaRepository extensions
│   ├── model          # @Entity classes
│   ├── dto            # Java records for API boundaries
│   ├── config         # @Configuration classes
│   ├── security       # Module-specific security (filters, handlers)
│   ├── facade         # Orchestrates multiple services (optional)
│   ├── mapper         # MapStruct mappers (@Mapper(componentModel="spring"))
│   ├── validator      # Custom ConstraintValidator annotations
│   └── exception      # Custom exceptions + @RestControllerAdvice
├── common
│   └── dto            # Shared DTOs (ApiResponse)
└── CourseAuraHubApplication.java
```

**Layer Rules:**
- Controllers → HTTP only (request/response, cookies, status codes)
- Services → Business logic, no HTTP types
- Repositories → Data access only
- Entities → 1:1 DB table mapping
- DTOs (records) → All API boundaries
- Facades → Multi-service orchestration
- Security → AuthZ/AuthN concerns only

## Core Conventions

| Area | Rule |
|------|------|
| **DI** | Constructor injection only (`@RequiredArgsConstructor` on `final` fields) |
| **Services** | Interface (`XyzService`) + Impl (`XyzServiceImpl`) |
| **Transactions** | `@Transactional` on write operations |
| **REST Base** | `/api/v1/{resource}` |
| **Responses** | `ApiResponse<T>` wrapper (success, message, data, errors, timestamp) |
| **Status Codes** | 200/201/204, 400/401/403/404/409, 500 |
| **Validation** | `@Valid` on DTOs, Bean Validation + custom validators, global handler |
| **Exceptions** | Custom per domain → mapped to HTTP status in `@RestControllerAdvice` |
| **DTOs** | Java `record` types, never expose entities |
| **Mapping** | MapStruct: ignore `id/createdAt/updatedAt/password`, constants for defaults, expressions for enums |
| **Migrations** | `V####__description.sql`, `CREATE IF NOT EXISTS`, `validate` DDL auto |
| **Tests** | Integration: `@SpringBootTest` + Testcontainers + MockMvc, extend `IntegrationTestBase` |
| **Logging** | `@Slf4j`, structured context (IDs, emails), no `System.out` |
| **Secrets** | Never read/print/commit `.env`, credentials, keys — all via env vars |

## Security Baseline (Configured in `SecurityConfig`)

- Stateless sessions, JWT in HttpOnly cookies (access 15min, refresh 7d/30d remember-me)
- Token rotation on refresh
- `JwtAuthFilter` → validates access token, sets `SecurityContext`
- OAuth2 login with custom success/failure handlers
- CSRF double-submit cookie (disabled for auth endpoints)
- CORS: credentials allowed, explicit headers

## Adding a New Module

1. Create Flyway migration `V####__create_{table}.sql`
2. Create `@Entity` in `model/`
3. Create `JpaRepository` in `repository/`
4. Create request/response `record` DTOs in `dto/` with validation
5. Create MapStruct mapper in `mapper/`
6. Create service interface + `@Service` impl in `service/`
7. Create `@RestController` in `controller/` with Swagger annotations
8. Add security rules in `SecurityConfig` if needed
9. Write integration tests extending `IntegrationTestBase`

## Quick Reference Patterns

```java
// Service
@Service @RequiredArgsConstructor @Transactional
class XyzServiceImpl implements XyzService { ... }

// Controller
@RestController @RequestMapping("/api/v1/xyz") @RequiredArgsConstructor @Tag(...)
class XyzController { ... }

// Mapper
@Mapper(componentModel = "spring")
interface XyzMapper { ... }

// Custom Validator
@Target(FIELD) @Retention(RUNTIME) @Constraint(validatedBy = XyzValidator.class)
@interface XyzConstraint { ... }

// Global Exception Handler (per module or shared)
@RestControllerAdvice(basePackages = "com.course_aura_hub.{module}")
class XyzExceptionHandler { ... }
```

## What to Avoid

- ❌ Field injection, business logic in controllers, entities in responses
- ❌ `ddl-auto: create/update`, hardcoded secrets, skipping validation
- ❌ Duplicate logic (use facade/shared service), unnecessary dependencies
- ❌ Refactoring unrelated code, running build/test/git commands unless asked

---

*Update when architecture evolves. Applies to all current and future modules.*