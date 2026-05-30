-- Fallback schema initialization (runs only if Hibernate DDL disabled)
-- Mirror of Flyway migrations for local development

-- This file is intentionally minimal; Hibernate ddl-auto=create handles
-- schema generation in local dev. Kept for reference and manual setup.

-- For full schema, see src/main/resources/db/migration/V*.sql
-- or run: ./gradlew flywayMigrate (production)

-- No-op: Hibernate creates tables from @Entity classes
