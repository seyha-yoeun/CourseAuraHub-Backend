-- V0057: Databases created by Hibernate before Flyway was baselined can still carry Hibernate's generated
-- courses_status_check, which predates PENDING_REVIEW and makes "send for review" fail. The migrations never
-- define this constraint, so dropping it makes those databases match a fresh install. A no-op elsewhere.

ALTER TABLE courses DROP CONSTRAINT IF EXISTS courses_status_check;
