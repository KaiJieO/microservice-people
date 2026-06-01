# PHASE 1: Entity & Database

**Timeline:** 3-4 days  
**Owner:** You  
**Status:** READY TO START

---

## What

Create database schema + JPA entity layer. Foundation for all services, controllers, audit logging.

**Deliverables:**
1. 5 JPA entities (Lombok annotated)
2. 5 Spring Data JPA repositories
3. 5 Flyway migrations (V1-V5)
4. CacheConfig bean (Redis setup)

---

## How

### Step 1: Create Entities (Lombok)

```java
@Entity
@Table(name = "user_credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(nullable = false, unique = true)
    private String email;
    // ... other fields

    @CreationTimestamp
    @Column(nullable = false, updatable = false,
            columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
```

**Pattern:** 
- `@Entity` + `@Table(name = "snake_case")`
- `@Data` (auto getter/setter/toString/equals/hashCode)
- `@NoArgsConstructor` (JPA requirement)
- `@AllArgsConstructor` (optional, for fluent creation)
- `@Id` + `@GeneratedValue(strategy = GenerationType.UUID)` on id field (Hibernate needs a generator to build the table)
- `@Column(nullable = false, unique = true)` on email, phone
- `@Enumerated(EnumType.STRING)` on status
- `@CreationTimestamp` / `@UpdateTimestamp` on timestamps, paired with `columnDefinition = "datetime default CURRENT_TIMESTAMP"`
  - Use plain `datetime` (seconds), NOT `datetime(6)`. Do NOT use `@ColumnDefault` — on a `datetime(6)` column its bare `CURRENT_TIMESTAMP` (precision 0) triggers MySQL `Invalid default value`. `columnDefinition` sets type + default together and sidesteps it.

### Step 2: Create Repositories

```java
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, String> {
    Optional<UserCredentials> findByEmail(String email);
    Optional<UserCredentials> findByPhone(String phone);
    // ... custom queries as needed
}
```

**Pattern:** Extend JpaRepository<Entity, IdType>. Add find* methods as needed.

### Step 3: Create Migrations (Flyway)

```sql
-- V1__Create_User_Credentials.sql
CREATE TABLE user_credentials (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    roles VARCHAR(255) DEFAULT 'USER',
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED') DEFAULT 'ACTIVE',
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    last_login_at DATETIME NULL,
    login_attempt_count INT DEFAULT 0,
    locked_until DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);
```

**Pattern:** Use Flyway sequential naming (V1, V2, V3...). SQL files in `src/main/resources/db/migration/`.

### Step 4: CacheConfig

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.create(factory);
    }
}
```

---

## Code Structure

```
src/main/java/com/microservice/people/
├── entity/
│   ├── UserCredentials.java              (user auth + signup)
│   ├── UserProfile.java     (profile, Malaysia-specific)
│   ├── PasswordResetToken.java      (temp reset tokens)
│   ├── UserSession.java             (active JWT sessions)
│   └── AuditLog.java                (audit trail)
├── repository/
│   ├── UserCredentialsRepository.java
│   ├── UserProfileRepository.java
│   ├── PasswordResetTokenRepository.java
│   ├── UserSessionRepository.java
│   └── AuditLogRepository.java
└── config/
    └── CacheConfig.java

src/main/resources/db/migration/
├── V1__Create_User_Credentials.sql
├── V2__Create_User_Profile.sql
├── V3__Create_Password_Reset_Tokens.sql
├── V4__Create_User_Sessions.sql
└── V5__Create_Audit_Logs.sql
```

---

## Entity Details

### 1. UserCredentials (root)
- `id` (UUID, PK)
- `email` (unique, not null)
- `phone` (unique, not null)
- `password_hash` (not null, BCrypt)
- `roles` (default 'USER')
- `status` (ENUM: ACTIVE, INACTIVE, SUSPENDED, DELETED)
- `email_verified`, `phone_verified` (boolean flags)
- `last_login_at`, `login_attempt_count`, `locked_until` (security tracking)
- `created_at`, `updated_at` (timestamps)

### 2. UserProfile (1:1 with UserCredentials)
- `id` (UUID, PK)
- `user_id` (FK → UserCredentials, unique)
- `salutation`, `first_name`, `last_name` (profile)
- `date_of_birth`, `gender` (demographics)
- `identity_type` (NRIC or PASSPORT)
- `identity_number` (Malaysia-specific)
- `citizenship` (MALAYSIAN, PR, FOREIGNER)
- `nationality`
- `address1`, `address2`, `address3`, `city` (address)
- `state` (ENUM: 16 Malaysian states)
- `postcode`
- `identity_verified` (KYC verification flag)
- `created_at`, `updated_at`

### 3. PasswordResetToken (N:1 with UserCredentials)
- `id` (UUID, PK)
- `user_id` (FK → UserCredentials)
- `email` (denormalized)
- `token_hash` (BCrypt hashed UUID, unique)
- `expires_at` (5 min TTL)
- `used_at` (one-time use marker)
- `created_at`

### 4. UserSession (N:1 with UserCredentials)
- `id` (UUID, PK)
- `user_id` (FK → UserCredentials)
- `token_hash` (JWT token hash, unique)
- `ip_address`, `user_agent` (request context)
- `expires_at` (24h TTL)
- `created_at`, `updated_at`

### 5. AuditLog (N:1 with UserCredentials)
- `id` (UUID, PK)
- `user_id` (FK → UserCredentials, nullable)
- `action` (CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAILED)
- `table_name` (which table changed)
- `record_id` (which record)
- `old_values` (JSON)
- `new_values` (JSON)
- `ip_address`, `user_agent` (request context)
- `created_at` (immutable)

---

## Dependencies

### Must Install First
- MySQL 8.0 running on localhost:3306
- Redis running on localhost:6379
- Java 17+

### Gradle Dependencies (already in build.gradle.kts)
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-redis`
- `flyway-core`, `flyway-mysql`
- `mysql-connector-j`
- `lombok`

---

## Security Considerations

1. **Soft Delete:** Never hard DELETE. Use `status = 'DELETED'` for recovery + audit trail.
2. **Timestamps:** Always track `created_at` (immutable) + `updated_at` (mutable).
3. **Unique Constraints:** Email + phone unique (prevent duplicates).
4. **Indexing:** Index email, phone, status, created_at (for filtering + pagination).
5. **Audit Logging:** Every change captured in audit_logs (legal requirement).
6. **No Secrets in Code:** Database creds in application.properties (environment-specific).
7. **Password Hash:** Never store plain password. BCrypt later (Phase 2).

---

## Code Review Checklist

- [ ] All 5 entities use Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor)
- [ ] All 5 repositories extend JpaRepository<Entity, String>
- [ ] All migrations follow V*__SnakeCase naming (Flyway standard)
- [ ] All migrations include CREATE TABLE + indexes + constraints
- [ ] CacheConfig bean defined + @EnableCaching present
- [ ] No hardcoded secrets in code (all in application.properties)
- [ ] Foreign keys use ON DELETE CASCADE (referential integrity)
- [ ] Indexes match TABLES.md exactly (email, phone, status, created_at)
- [ ] Enums use ENUM type in MySQL (not string)
- [ ] Timestamps use DATETIME DEFAULT CURRENT_TIMESTAMP (plain datetime, not datetime(6))
- [ ] Soft delete uses status ENUM (not is_deleted flag)
- [ ] AuditLog table schema matches TABLES.md

---

## Testing Checklist

### Unit Tests (Not needed Phase 1 — entities are data containers)

### Integration Tests
- [ ] MySQL connection works (./gradlew build succeeds)
- [ ] All 5 migrations run without error (flyway.baseline-on-migrate=true)
- [ ] Each table queryable via MySQL CLI
- [ ] Foreign keys enforced (insert without FK fails)
- [ ] Unique constraints enforced (duplicate email fails)
- [ ] Indexes created (SHOW INDEXES FROM user_credentials)

### Manual Tests
- [ ] Start app: `./gradlew bootRun`
- [ ] Check logs: "Starting MicroservicePeopleApplication"
- [ ] No Flyway errors in logs
- [ ] Query MySQL:
  ```sql
  USE microservice_people_db;
  SHOW TABLES;  -- should show 5 tables
  DESC user_credentials;  -- verify columns
  SHOW INDEXES FROM user_credentials;  -- verify indexes
  ```
- [ ] Redis ping responds: `redis-cli ping` → PONG
- [ ] Can reach app: `curl http://localhost:8081/actuator/health` (should error 404 but app responds)

---

## Success Criteria

✓ All 5 entities compile without error  
✓ All 5 repositories created (Spring will auto-generate implementations)  
✓ All 5 migrations execute on startup (flyway.baseline-on-migrate=true)  
✓ `./gradlew build` succeeds  
✓ App starts: `./gradlew bootRun` → "Started MicroservicePeopleApplication"  
✓ All 5 tables exist in MySQL (verified with SHOW TABLES)  
✓ Foreign keys + indexes + constraints in place (verified with DESC)  
✓ No Flyway errors in logs  
✓ Redis connection initialized (no Redis errors in logs)  

---

## Blockers & Mitigations

| Blocker | Mitigation |
|---|---|
| MySQL not running | Start before Phase 1: `mysql.server start` (Mac) or MySQL Service (Windows) |
| Redis not running | Start before Phase 1: `redis-server` or Redis Service |
| Flyway migration fails | Check SQL syntax, ensure table doesn't already exist, check MySQL version |
| Port 3306 in use | Change datasource.url in application.properties or kill MySQL process |
| Lombok not working | Ensure `compileOnly` + `annotationProcessor` both in build.gradle.kts |

---

## Effort Estimate

- **Entity creation (5 entities):** 30 min
- **Repository creation (5 repos):** 20 min
- **Migration writing (5 migrations):** 90 min (SQL schema is complex)
- **CacheConfig:** 10 min
- **Testing + verification:** 60 min (ensure all migrations work, indexes created, etc.)

**Total:** ~3.5-4 hours (1 working day)

---

## Owner & Timeline

**Owner:** You  
**Start Date:** 2026-05-30  
**Duration:** 3-4 days (includes testing + debugging)  
**End Date:** 2026-06-02 (estimate)  

---

## Next Phase

Once Phase 1 complete + all success criteria met:
- **Phase 2:** Auth + Services (JwtTokenProvider, AuthService, PasswordService, UserService)

---

## References

- **PLAN.md:** Architecture overview + endpoint definitions
- **TABLES.md:** Complete schema reference (copy SQL from here)
- **SPRINGBOOT_STANDARDS.md:** Entity + repository patterns
- **application.properties:** Already configured (MySQL + Redis + Flyway)

---

## Local DB Strategy

Local dev uses **Flyway + validate** (same path as prod — dev/prod parity):

```properties
spring.jpa.hibernate.ddl-auto=validate    # entities must match migrations
spring.flyway.enabled=true                # migrations = source of truth
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.sql.init.mode=never
```

- Flyway runs V1–V5 on boot, records them in `flyway_schema_history`, skips already-applied → **data persists across restarts**.
- Hibernate `validate` runs after Flyway: any entity ↔ migration mismatch (type, length, nullability) **aborts startup**. This is the parity guarantee.
- Schema changes now require a new `V*.sql` migration (not just editing the entity).

---

## Test Commands

### Prerequisites (must be running)
```powershell
mysql -u root --password=test123 -e "SELECT 1;"   # MySQL on :3306
redis-cli ping                                     # Redis on :6379 → PONG
```

### 1. Build + unit tests
```powershell
.\gradlew.bat clean build --no-daemon
```
> Windows: use `gradlew.bat --no-daemon` (bash `./gradlew` can hang on a stuck daemon).

### 2. Run app (Flyway migrate → validate → start)
```powershell
.\gradlew.bat bootRun --no-daemon
```
Expect: `Successfully applied 5 migrations ... v5` then `Started MicroservicePeopleApplication`.

### 3. Verify schema + migration history
```powershell
mysql -u root --password=test123 microservice_people_db -e "SHOW TABLES;"
mysql -u root --password=test123 microservice_people_db -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

### 4. Load sample data (once — persists)
```powershell
mysql -u root --password=test123 microservice_people_db < src\main\resources\test_data.sql
```
Expected rows: user_credentials 4, user_profile 4, password_reset_tokens 2, user_sessions 3, audit_logs 4.

### 5. Query data
```powershell
mysql -u root --password=test123 microservice_people_db -e "SELECT id, email, status FROM user_credentials;"
```

### 6. Persistence test (restart keeps data)
```powershell
# Ctrl+C, then rerun — Flyway skips applied migrations, data intact
.\gradlew.bat bootRun --no-daemon
mysql -u root --password=test123 microservice_people_db -e "SELECT COUNT(*) FROM user_credentials;"   # → 4
```

### 7. Clean reset (fresh DB from V1)
```powershell
mysql -u root --password=test123 -e "DROP DATABASE microservice_people_db; CREATE DATABASE microservice_people_db;"
.\gradlew.bat bootRun --no-daemon
```

---

## Last Updated

2026-05-30 — Phase 1 entities + repositories + migrations; local switched to Flyway + validate (dev/prod parity)
