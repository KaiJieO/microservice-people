# Database Strategy — Local vs Production

**Purpose:** Document schema management approach across development and production environments.

---

## What is DDL?

**DDL = Data Definition Language**

SQL commands that define database structure:
- `CREATE TABLE` — make table
- `ALTER TABLE` — change table structure
- `DROP TABLE` — delete table
- `CREATE INDEX` — create index

**JPA/Hibernate uses DDL to:**
- Read entity definitions (e.g., `@Entity`, `@Column`)
- Auto-generate CREATE TABLE statements
- Execute DDL on app startup (based on `spring.jpa.hibernate.ddl-auto`)

---

## Local Development Strategy

**Goal:** Fast iteration. Instant schema updates without manual SQL.

### Configuration
```properties
# application.properties (local dev)
spring.jpa.hibernate.ddl-auto=create
spring.sql.init.mode=always
spring.flyway.enabled=false
```

### How It Works
1. Define entity (e.g., `UserSignup.java`)
2. Start app: `./gradlew bootRun`
3. Hibernate reads entities
4. Hibernate generates DDL (CREATE TABLE statements)
5. Hibernate executes DDL
6. Tables exist in MySQL
7. App runs

### Changes
```
Code change (add @Column) 
  → Restart app 
  → Hibernate re-reads entity 
  → DDL runs again 
  → Schema updated instantly
```

### Pros
- Fast iteration (no manual SQL)
- Entity = source of truth
- Zero boilerplate

### Cons
- No audit trail (who changed what, when?)
- No rollback capability (recreate loses data)
- Not production-grade

---

## Production Strategy

**Goal:** Safe, versioned, auditable schema changes.

### Configuration
```properties
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.sql.init.mode=never
```

### How It Works
1. Write migration SQL (e.g., `V1__Create_User_Signup.sql`)
2. Commit to git
3. Deploy app
4. Flyway reads migrations from `classpath:db/migration`
5. Flyway checks `flyway_schema_history` table (which migrations ran)
6. Flyway executes new migrations only
7. `spring.jpa.hibernate.ddl-auto=validate` ensures entities match schema

### Changes
```
Feature branch → Write V6__Add_Column.sql 
  → PR review 
  → Merge to main 
  → Deploy 
  → Flyway runs V6 
  → Schema updated safely
```

### Pros
- Full audit trail (git history + flyway_schema_history table)
- Rollback capability (revert to previous migration)
- Production-grade (no data loss)
- CI/CD friendly (migrations tested before deploy)

### Cons
- Manual SQL (slower than code-first)
- Boilerplate (write both entity + migration)

---

## Comparison Table

| Aspect | Local (Hibernate DDL) | Production (Flyway) |
|---|---|---|
| **Iteration Speed** | Instant (restart app) | Slower (write SQL + commit) |
| **Source of Truth** | Entity code | Migration SQL files |
| **Schema Audit Trail** | None | Full (git + flyway table) |
| **Rollback** | Lose data (recreate) | Safe (revert version) |
| **Version Control** | Entity changes tracked | All migrations in git |
| **CI/CD** | Not applicable | Required (migrations tested) |
| **Production Safe** | No | Yes |
| **Data Loss Risk** | High (recreate on startup) | Low (append-only migrations) |

---

## Flyway Key Concepts

### Migration Files
```
V1__Create_User_Signup.sql          (run first, never change)
V2__Create_User_Personal_Details.sql (run second, never change)
V3__Create_Password_Reset_Tokens.sql (run third, never change)
```

**Rules:**
- `V` prefix (versioned migration)
- Double underscore (`__`) separator
- Number increases (V1, V2, V3, etc.)
- Never modify once committed (immutable)
- Append-only (new migrations always added to end)

### Flyway Schema History Table
```sql
CREATE TABLE flyway_schema_history (
    version INT,
    description VARCHAR(255),
    type VARCHAR(20),
    script VARCHAR(1000),
    checksum INT,
    installed_by VARCHAR(100),
    installed_on TIMESTAMP,
    execution_time INT,
    success BOOLEAN
);
```

Flyway uses this to track which migrations have run. Example:
```
version | description | success | installed_on
1       | Create User Signup | TRUE | 2026-05-30 10:00:00
2       | Create User Personal Details | TRUE | 2026-05-30 10:00:05
3       | Create Password Reset Tokens | TRUE | 2026-05-30 10:00:10
```

---

## Spring Profile Usage

### Run Locally (Hibernate DDL)
```bash
./gradlew bootRun
# Uses application.properties (default profile)
# spring.jpa.hibernate.ddl-auto=create
# spring.flyway.enabled=false
```

### Run in Production (Flyway)
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
# Uses application-prod.properties
# spring.jpa.hibernate.ddl-auto=validate
# spring.flyway.enabled=true
```

### Create Environment-Specific Configs
```
src/main/resources/
├── application.properties          (local: Hibernate DDL)
├── application-prod.properties     (production: Flyway)
├── application-test.properties     (testing: in-memory H2)
```

---

## Schema.sql (Fallback)

**Purpose:** Safety net if Hibernate DDL fails.

Create `src/main/resources/schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS user_signup (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    ...
);

CREATE TABLE IF NOT EXISTS user_personal_details (
    ...
);

-- ... all 5 tables
```

**When it runs:**
- `spring.sql.init.mode=always` → Spring loads schema.sql on startup
- If tables don't exist, schema.sql creates them
- If tables exist (from Hibernate), schema.sql skips (CREATE TABLE IF NOT EXISTS)

**Fallback order:**
1. Hibernate DDL (primary)
2. schema.sql (secondary, if Hibernate fails)
3. Manual creation (last resort)

---

## Best Practices

### Local Development
1. Update entity code
2. Restart app
3. Hibernate auto-creates schema
4. Test feature
5. Commit entity code

### Production Deployment
1. Write migration SQL (V*__*.sql)
2. Commit migration + entity code together
3. PR review (both SQL + entity)
4. Merge to main
5. Deploy app
6. Flyway runs migrations
7. Validate with `ddl-auto=validate`

### Never Do
❌ Manually edit database tables in production (use migrations)  
❌ Edit migration SQL after committing (immutable)  
❌ Use `ddl-auto=create` in production (data loss)  
❌ Disable Flyway in production (lose audit trail)  
❌ Commit without migrations (schema drift)

---

## Troubleshooting

### "Unknown database 'microservice_people_db'"
```
Error: Cannot connect to database
Cause: Database doesn't exist
Fix: Create manually once
  mysql -u root -p test123 -e "CREATE DATABASE microservice_people_db;"
```

### "Flyway: Unknown migration with version 1"
```
Error: Migration files not found
Cause: Migrations in wrong location or wrong naming
Fix: Ensure V1__*.sql in src/main/resources/db/migration/
```

### "Hibernation conflicts with Flyway"
```
Error: DDL executed twice (Hibernate + Flyway)
Cause: Both enabled simultaneously
Fix: In prod config, set spring.jpa.hibernate.ddl-auto=validate
```

### "Invalid default value for 'created_at'"
```
Error: CREATE TABLE fails on timestamp column
Cause: Hibernate maps LocalDateTime to datetime(6); @ColumnDefault("CURRENT_TIMESTAMP")
       emits a precision-0 default → MySQL 8 rejects the precision mismatch
Fix: Drop @ColumnDefault. Use @Column(columnDefinition = "datetime default CURRENT_TIMESTAMP")
     (plain datetime = seconds; sets type + default together)
```

### "Table doesn't exist" with ddl-auto=create
```
Error: CREATE TABLE silently skipped, then index/constraint DDL fails "table doesn't exist"
Cause: @Id String field has no generator → Hibernate cannot create the table
Fix: Add @GeneratedValue(strategy = GenerationType.UUID) on the id field
```

### "Data gone after restart" (local dev)
```
Cause: ddl-auto=create DROPS + recreates all tables on every boot
Fix: Reload test_data.sql after each boot, or switch to ddl-auto=update to persist
```

### "gradlew hangs with no output"
```
Cause: Stuck Gradle daemon
Fix: Run via gradlew.bat <task> --no-daemon
```

---

## Migration Example

### Step 1: Entity Change (Local)
```java
@Entity
public class UserSignup {
    @Column(nullable = false)
    private String email;
    
    @Column  // New field
    private String phone;
}
```

### Step 2: Restart (Local)
```bash
./gradlew bootRun
# Hibernate generates: ALTER TABLE user_signup ADD COLUMN phone VARCHAR(20);
# Schema updated instantly
```

### Step 3: Migration (Before Prod Deploy)
```sql
-- V6__Add_Phone_To_User_Signup.sql
ALTER TABLE user_signup ADD COLUMN phone VARCHAR(20);
```

### Step 4: Deploy (Prod)
```bash
# app-prod boots with Flyway enabled
# Flyway runs V6__Add_Phone_To_User_Signup.sql
# Schema updated safely with audit trail
```

---

## References

- **Flyway Docs:** https://flywaydb.org/documentation/
- **Spring Boot JPA:** https://spring.io/projects/spring-data-jpa
- **Hibernate DDL:** https://hibernate.org/orm/documentation/

---

**Last Updated:** 2026-05-30

**Version:** 1.0

**Used By:** People Microservice, qr-microservice, all future Spring Boot services
