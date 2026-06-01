# People Microservice — Build Plan

## Context
Building People microservice at `/home/kai/Documents/GitHub/microservice-people`. Mirrors qr-microservice structure. Handles: signup, login, sessions, password reset, profile CRUD. Malaysia-specific user data (NRIC, address, state). Other services call via service-to-service (Pattern A). **LOCAL DEVELOPMENT ONLY** — MySQL + Redis run locally (no Docker). **Portfolio project, may become industry standard** → design for future scalability from Phase 1.

---

## ⚠️ LOCAL DEVELOPMENT SETUP (CRITICAL)

**This project runs 100% locally. No Docker, no cloud services.**

### Prerequisites (Must Install)
```
✓ Java 17+ (spring-boot-starter-web requirement)
✓ MySQL 8.0 (local instance on port 3306)
✓ Redis (local instance on port 6379)
✓ Gradle 8.0+ (wrapper provided in repo)
```

### Local Service Ports
```
MySQL:       localhost:3306
Redis:       localhost:6379
App:         localhost:8081
```

### Before Running App
```bash
# 1. Verify MySQL running
mysql -u root -p  # password: test123
SELECT 1;  # should respond

# 2. Verify Redis running
redis-cli ping  # should respond: PONG

# 3. Build + run
./gradlew build
./gradlew bootRun  # or run from IDE
```

### Troubleshooting Local Setup
```
MySQL not running?
  → Start: mysql.server start (Mac) or MySQL Service (Windows)
  → Port 3306 in use? Kill process or change application.yml

Redis not running?
  → Start: redis-server (CLI) or Redis Service (Windows)
  → Port 6379 in use? Kill process or change application.yml

Connection refused errors?
  → Check application.yml datasource/redis config
  → Verify ports 3306 + 6379 accessible
  → Check firewall rules
```

---

---

## Project Structure

```
microservice-people/
├── backend/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── .gitignore
│   └── src/
│       ├── main/
│       │   ├── java/com/people/
│       │   │   ├── MicroservicePeopleApplication.java
│       │   │   ├── controller/
│       │   │   │   ├── UserController.java
│       │   │   │   ├── AuthController.java
│       │   │   │   ├── PasswordController.java
│       │   │   │   └── SessionController.java
│       │   │   ├── service/
│       │   │   │   ├── UserService.java
│       │   │   │   ├── PasswordService.java
│       │   │   │   ├── SessionService.java
│       │   │   │   └── AuditService.java
│       │   │   ├── repository/
│       │   │   │   ├── UserCredentialsRepository.java
│       │   │   │   ├── UserProfileRepository.java
│       │   │   │   ├── PasswordResetTokenRepository.java
│       │   │   │   ├── UserSessionRepository.java
│       │   │   │   └── AuditLogRepository.java
│       │   │   ├── entity/
│       │   │   │   ├── UserCredentials.java
│       │   │   │   ├── UserProfile.java
│       │   │   │   ├── PasswordResetToken.java
│       │   │   │   ├── UserSession.java
│       │   │   │   └── AuditLog.java
│       │   │   ├── dto/
│       │   │   │   ├── SignupRequest.java
│       │   │   │   ├── LoginRequest.java
│       │   │   │   ├── UserResponse.java
│       │   │   │   ├── UserProfileResponse.java
│       │   │   │   ├── UpdateProfileRequest.java
│       │   │   │   ├── PasswordResetRequest.java
│       │   │   │   └── ErrorResponse.java
│       │   │   ├── exception/
│       │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   ├── UserAlreadyExistsException.java
│       │   │   │   ├── UserNotFoundException.java
│       │   │   │   ├── InvalidPasswordException.java
│       │   │   │   ├── AuthenticationFailedException.java
│       │   │   │   ├── InvalidResetTokenException.java
│       │   │   │   └── UnauthorizedAccessException.java
│       │   │   ├── security/
│       │   │   │   ├── JwtTokenProvider.java
│       │   │   │   └── RbacInterceptor.java
│       │   │   └── config/
│       │   │       ├── CacheConfig.java
│       │   │       ├── SecurityConfig.java
│       │   │       └── ValidationConfig.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           ├── V1__Create_User_Credentials.sql
│       │           ├── V2__Create_User_Profile.sql
│       │           ├── V3__Create_Password_Reset_Tokens.sql
│       │           ├── V4__Create_User_Sessions.sql
│       │           └── V5__Create_Audit_Logs.sql
│       └── test/java/com/people/
│           ├── controller/UserControllerTest.java
│           ├── service/UserServiceTest.java
│           ├── service/PasswordServiceTest.java
│           └── integration/UserFlowIntegrationTest.java
├── PLAN.md                    ← this file
├── TABLES.md                  ← database schema reference
├── .gitignore
└── .env.example
```

---

## build.gradle.kts
Same as qr-microservice with 2 changes:
- **Remove:** `net.glxn.qrgen:javase` (QR-specific, not needed)
- **Add:** `spring-boot-starter-data-redis` + `redis.clients:jedis`

### Lombok Integration
Lombok included to reduce boilerplate on entities + DTOs.

**Annotations used:**
- `@Data` — generates getter/setter/toString/equals/hashCode (JPA entities)
- `@AllArgsConstructor` — generates constructor with all fields (DTOs)
- `@NoArgsConstructor` — generates zero-arg constructor (JPA requirement)
- `@Builder` — generates fluent builder pattern (optional, for readability)

**Example:**
```java
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String email;
    private String passwordHash;
}
// Auto-generates: getters, setters, toString(), equals(), hashCode(), constructors
```

**Why:** Reduces code noise, keeps files < 100 lines, focus on logic not boilerplate.

```gradle
plugins {
    java
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}
group = "com.microservice.people"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_17

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("redis.clients:jedis")
    implementation("org.flywaydb:flyway-core:10.4.1")
    implementation("org.flywaydb:flyway-mysql:10.4.1")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
springBoot { mainClass.set("com.microservice.people.MicroservicePeopleApplication") }
```

---

## application.properties
**IMPORTANT:** Local development only. MySQL + Redis must run on local ports. DO NOT change ports unless services moved.

```properties
# Application
spring.application.name=microservice-people
server.port=8081

# MySQL (local instance, port 3306; auto-creates DB on first run)
spring.datasource.url=jdbc:mysql://localhost:3306/microservice_people_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=test123
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate (local dev: Flyway owns schema; validate entities match)
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# SQL initialization (disabled; data persists, load test_data.sql manually once)
spring.sql.init.mode=never

# Flyway (database migrations — source of truth, same as prod)
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true

# Redis (local instance, port 6379)
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.timeout=2000

# Caching (Redis backend)
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000
spring.cache.redis.use-key-prefix=true

# Logging
logging.level.com.microservice.people=DEBUG
```

---

## CacheConfig.java
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

## Caching Pattern

**TTL:** 1 hour passive expiry (Redis, configured in application.yml)

**Manual evict on write:** `@CacheEvict` fires after update/delete

**Cached Endpoints:**
```
GET /api/users/{id} → Cache key: "users::{id}"
  ├─ First call: Miss → fetch from DB → store in Redis (1 hour TTL)
  ├─ Second call: Hit → return from Redis
  └─ Next call after 1 hour: Expired → refetch from DB

GET /api/users?page=0&size=20 → Cache key: "users::page:0:size:20"
  └─ Admin only, cache for listing
```

**Non-cached Endpoints:**
```
POST /api/auth/signup → Always hits DB (create)
POST /api/auth/login → Always hits DB (authentication)
POST /api/passwords/reset-request → Always hits DB (create token)
POST /api/passwords/reset → Always hits DB (update password)
```

**Cache Invalidation:**
```java
// Update profile → evict user cache
@CacheEvict(value = "users", key = "#userId")
public void updateProfile(String userId, UpdateProfileRequest req) { ... }

// Change password → evict user cache (hash changed)
@CacheEvict(value = "users", key = "#userId")
public void changePassword(String userId, String newPassword) { ... }

// Delete user → evict user cache
@CacheEvict(value = "users", key = "#userId")
public void deleteUser(String userId) { ... }
```

**Strategy:** Hybrid (TTL + manual evict) — balances freshness with performance

---

## Key Decisions Made

| Decision | Choice | Rationale |
|---|---|---|
| Project Type | Portfolio → Industry Standard | Design for scalability from Phase 1 |
| Password Hashing | BCrypt | Enterprise standard, auto-salt |
| Password Rules | 8+ chars, 1 upper, 1 lower, 1 digit, 1 symbol | Industry standard strength |
| Reset Token TTL | 5 minutes | Standard practice, time-bound security |
| Email Verification | After reset, link in email | User confirmation, not overkill |
| Service Auth | API Key (Phase 1, JWT later) | Simple now, scalable future |
| Database Design | Separate tables per domain | Future-proof, enables KYC scaling |
| Soft Delete | Mark status = DELETED | Audit trail, recovery option, compliance |
| Audit Logging | Centralized audit_logs table | Single source of truth, full history |
| Controllers | Multiple (Auth, User, Password, Session) | Clear separation, easier to extend |
| RBAC | USER, ADMIN roles | Simple, sufficient for Phase 1 |
| Caching | Redis (hybrid TTL + evict) | Balance freshness & performance |

---

## Next Steps

**PHASE 1: Entity & Database**
- Create entities: UserCredentials, UserProfile, PasswordResetToken, UserSession, AuditLog
- Create repositories (JPA interfaces)
- Create migrations (V1-V5)

**PHASE 2: Authentication & Services**
- AuthService (signup, login, JWT token generation)
- PasswordService (reset flow, token validation)
- UserService (CRUD, profile management)
- Exception handling & validation

**PHASE 3: Controllers & API**
- AuthController, UserController, PasswordController, SessionController
- RBAC guards (@PreAuthorize)
- Endpoint routing & pagination

**PHASE 4: Caching & Audit**
- CacheConfig (Redis setup)
- AuditService (log all changes)
- Cache invalidation on CRUD

**PHASE 5: Testing & Verification**
- Unit tests (services)
- Integration tests (API)
- E2E flow tests
- Verify checklist

---

## REST Endpoints

### AuthController
| Method | Path | Auth | Action |
|---|---|---|---|
| POST | /api/auth/signup | None | Register user (email, phone, password) |
| POST | /api/auth/login | None | Authenticate (email/password → JWT token) |

### UserController
| Method | Path | Auth | Action |
|---|---|---|---|
| GET | /api/users/{id} | User | Get user profile (cached, self only) |
| GET | /api/users | Admin | Get all users (pagination, filtering, backoffice) |
| PUT | /api/users/{id} | User | Update user profile (self) |
| PUT | /api/users/{id}/email | Admin | Update user email (admin only) |
| DELETE | /api/users/{id} | Admin | Soft delete user (mark deleted) |

### PasswordController
| Method | Path | Auth | Action |
|---|---|---|---|
| POST | /api/passwords/reset-request | None | Request password reset (email → reset link) |
| POST | /api/passwords/reset | None | Reset password (token + new password) |
| PUT | /api/passwords/{id} | User | Change password (old + new, user self) |

### SessionController
| Method | Path | Auth | Action |
|---|---|---|---|
| GET | /api/sessions/{id} | User | Get session details (self only) |
| GET | /api/sessions/user/{id} | User | List user sessions (self only) |
| DELETE | /api/sessions/{id} | User | Logout (delete session) |
| DELETE | /api/sessions/user/{id}/all | User | Logout all devices (delete all user sessions) |

---

### Pagination & Filtering (List Endpoints)
```
GET /api/users?page=0&size=20&status=ACTIVE&citizenship=MALAYSIAN
├─ page: 0-indexed (default 0)
├─ size: rows per page (default 20, max 100)
├─ status: filter by status (ACTIVE, INACTIVE, SUSPENDED)
├─ citizenship: filter by citizenship (MALAYSIAN, PR, FOREIGNER)
└─ sortBy: field to sort (created_at, email)
```

---

### Auth Levels
```
NONE: Public endpoint (signup, login, reset-request)
USER: Authenticated user (can access own data, self operations)
ADMIN: Admin role only (list all users, delete users, change others' email)
```

---

## Database Schema

**See TABLES.md for complete schema definition.**

### Core Tables (Phase 1)
- `user_credentials` — email, phone, password_hash, roles, status, verification flags
- `user_profile` — Malaysia-specific profile (NRIC, address, state, citizenship)
- `password_reset_tokens` — temporary tokens (5 min expiry)
- `user_sessions` — active JWT sessions
- `audit_logs` — complete audit trail of all changes

### Future Tables (Phase 2+)
- `user_documents` — KYC uploads (passport, NRIC, proof of address)
- `user_verifications` — KYC verification status
- `user_bank_accounts` — payment details (encrypted)
- `user_devices` — device tracking for security
- `user_preferences` — user settings (language, timezone, notifications)

### Migrations
```
V1__Create_User_Credentials.sql
V2__Create_User_Profile.sql
V3__Create_Password_Reset_Tokens.sql
V4__Create_User_Sessions.sql
V5__Create_Audit_Logs.sql
```

**Flyway:** Sequential migrations, append-only. New migrations added to migration folder, never reset DB.

---

## Password Policy

**Requirements:**
- Minimum 8 characters
- At least 1 uppercase letter (A-Z)
- At least 1 lowercase letter (a-z)
- At least 1 numeric digit (0-9)
- At least 1 special symbol (!@#$%^&*)

**Hashing:** BCrypt with auto-generated salt (Spring Security `PasswordEncoder`)

**Example:** `MyP@ssw0rd` ✓ (meets all requirements)

---

## Authentication & Authorization

### JWT Token
- **Token Provider:** `JwtTokenProvider` (generate, validate, refresh)
- **Claims:** user_id, email, roles
- **Expiry:** 24 hours (configurable in application.yml)
- **Refresh Token:** Rotate on each login (future: implement refresh endpoint)
- **Storage:** HTTP-only cookie (secure, not accessible to JS)

### RBAC (Role-Based Access Control)
**Roles:**
- `USER` — default, can access own data
- `ADMIN` — manage all users, delete users, change emails, view audit logs

**Implementation:**
```java
@PreAuthorize("hasRole('ADMIN')")
public List<UserResponse> getAllUsers() { ... }

@PreAuthorize("hasRole('USER')")
public UserResponse getUserById(String id) { 
    // Allow if id == currentUser.id OR currentUser.isAdmin()
}
```

---

## Password Reset Flow

**Step 1:** User requests reset
```
POST /api/passwords/reset-request
Body: { "email": "user@example.com" }
↓
Service: Generate UUID token, hash it, store in password_reset_tokens table
TTL: 5 minutes (expires_at = NOW() + 5min)
↓
Send email: "Click link to reset: https://app.com/reset?token=<UUID>"
↓
Response: { "message": "Reset link sent to email" }
```

**Step 2:** User clicks link and resets password
```
POST /api/passwords/reset
Body: { "token": "<UUID>", "newPassword": "NewP@ssw0rd" }
↓
Service: Validate token (exists, not expired, hash matches)
         Hash new password with BCrypt
         Update user_credentials.password_hash
         Delete token (mark used_at = NOW())
↓
Audit Log: { "action": "PASSWORD_RESET", "user_id": "...", "timestamp": NOW() }
↓
Response: { "message": "Password reset successfully" }
```

**Security:**
- Token expires after 5 minutes (can't reuse old links)
- Token one-time use (mark used_at after reset)
- Email verification required (confirm reset was user's request)
- Log all password changes to audit_logs

---

## Exception Handling

| Exception | HTTP | Message | When |
|---|---|---|---|
| `UserAlreadyExistsException` | 409 Conflict | User with email already exists | Signup, email taken |
| `UserNotFoundException` | 404 Not Found | User not found | GET /api/users/{id}, user deleted |
| `InvalidPasswordException` | 400 Bad Request | Password does not meet requirements | Signup, password reset |
| `AuthenticationFailedException` | 401 Unauthorized | Invalid email or password | Login with wrong credentials |
| `InvalidResetTokenException` | 400 Bad Request | Reset token expired or invalid | Password reset with old/wrong token |
| `UnauthorizedAccessException` | 403 Forbidden | User lacks permission | GET /api/users (non-admin) |
| `ValidationException` | 400 Bad Request | Input validation failed | Missing required fields |

**GlobalExceptionHandler:** Catches all exceptions, returns consistent `ErrorResponse` format
```java
{
    "timestamp": "2026-05-29T10:30:00Z",
    "status": 400,
    "error": "ValidationException",
    "message": "Email is required",
    "path": "/api/auth/signup"
}
```

---

## Soft Delete Strategy

**Instead of hard DELETE:** Mark user as deleted (preserve audit trail)

```java
// Delete endpoint (Admin only)
DELETE /api/users/{id}
↓
Update user_credentials SET status = 'DELETED', updated_at = NOW() WHERE id = ?
↓
Audit Log: { "action": "DELETE", "table": "user_credentials", "user_id": "..." }
↓
Query filter: WHERE status != 'DELETED' (auto-exclude deleted users)

// Recovery (if needed)
Update user_credentials SET status = 'ACTIVE' WHERE id = ? AND status = 'DELETED'
```

**Why:** Comply with audit requirements, enable recovery, maintain referential integrity

---

## Audit Logging

**Every change to any table is logged:**

```java
// Insert
INSERT INTO audit_logs 
(user_id, action, table_name, record_id, new_values, created_at)
VALUES (?, 'CREATE', 'user_credentials', ?, JSON_OBJECT(...), NOW());

// Update
INSERT INTO audit_logs 
(user_id, action, table_name, record_id, old_values, new_values)
VALUES (?, 'UPDATE', 'user_profile', ?, old_json, new_json);

// Delete (soft)
INSERT INTO audit_logs 
(user_id, action, table_name, record_id, new_values)
VALUES (?, 'DELETE', 'user_credentials', ?, JSON_OBJECT('status', 'DELETED'));

// Login
INSERT INTO audit_logs 
(user_id, action, table_name, ip_address, user_agent)
VALUES (?, 'LOGIN', 'user_sessions', ?, ip, agent);

// Login failed
INSERT INTO audit_logs 
(user_id, action, table_name, ip_address)
VALUES (?, 'LOGIN_FAILED', 'user_credentials', ip);
```

**Retention:** Permanent (no cleanup, legal compliance)

---

## Service-to-Service Authentication (Pattern A)

**Other microservices call this service for user data.**

**Authentication Method:** API Key (simple for Phase 1)

```
GET /api/users/{id}
Header: X-Service-Key: <shared-secret>
↓
Middleware validates key
↓
Log service access in audit_logs: { "accessed_by_service": "qr-service" }
↓
Return UserResponse { id, email, first_name, last_name, created_at }
```

**Future:** JWT service tokens, mTLS when needed

---

## Deployment

**Phase 1 (Current):** Local development only
- Java app runs locally (`./gradlew bootRun`)
- MySQL 8.0 local instance
- Redis local instance
- No Docker, no cloud deployment

**Phase 2+ (Future):** Containerization & Cloud
- Dockerize app + MySQL + Redis
- Deploy to cloud (AWS/GCP/Azure)
- Use managed MySQL (RDS) + Redis (ElastiCache)
- See DEPLOYMENT.md when ready

---

## Future Enhancements (noted from architecture decisions)
- Circuit breaker (Resilience4j) — when other services consume this in production
- Event-based cache invalidation (RabbitMQ/Kafka) — when consistency becomes critical
- Distributed tracing (Jaeger)
- Rate limiting on endpoints
- Email service integration (SMTP gateway or AWS SES)

---

## Verification Checklist (Post-Implementation)

**Local Setup (BEFORE anything else)**
0. MySQL running on localhost:3306 (`mysql -u root -p`)
1. Redis running on localhost:6379 (`redis-cli ping`)
2. Verify both services respond before running app

**Build & Start**
3. `./gradlew build` → compiles clean
4. `./gradlew bootRun` → starts on port 8081 (check logs for "Started MicroservicePeopleApplication")

**Signup Flow**
5. POST `/api/auth/signup` with valid password → user created in user_credentials + user_profile
6. POST `/api/auth/signup` with same email → 409 Conflict (duplicate prevention)
7. POST `/api/auth/signup` with weak password (e.g., "123") → 400 Invalid Password

**Login Flow**
8. POST `/api/auth/login` with correct credentials → JWT token returned
9. POST `/api/auth/login` with wrong password → 401 Unauthorized
10. POST `/api/auth/login` with non-existent email → 401 Unauthorized

**User Profile**
11. GET `/api/users/{id}` with auth token → returns user profile (cached on Redis, first call hits DB)
12. GET `/api/users/{id}` second time → hits Redis cache (no DB query, verify Redis keys with `redis-cli KEYS '*'`)
13. GET `/api/users` without admin role → 403 Forbidden
14. GET `/api/users` with admin role → returns all users paginated

**Update User**
15. PUT `/api/users/{id}` as self → can update first_name, last_name, password
16. PUT `/api/users/{id}` as non-admin → cannot update email (403)
17. PUT `/api/users/{id}` with new password → cache evicted (verify with `redis-cli`) → next GET fetches fresh

**Password Reset**
18. POST `/api/passwords/reset-request` → token stored (5 min expiry) — NO EMAIL SENT (Phase 1: mock only, log to console)
19. POST `/api/passwords/reset` with valid token → password updated in DB
20. POST `/api/passwords/reset` with expired token → 400 Invalid Token
21. POST `/api/passwords/reset` with same token twice → 400 Token Already Used

**Sessions**
22. GET `/api/sessions/user/{id}` → lists all active sessions
23. DELETE `/api/sessions/{id}` → logout (delete specific session)
24. DELETE `/api/sessions/user/{id}/all` → logout all devices (delete all sessions)

**Delete User**
25. DELETE `/api/users/{id}` as admin → soft delete (status = DELETED)
26. GET `/api/users/{id}` deleted user → 404 Not Found (filtered out)
27. Verify audit_logs contains DELETE entry

**Audit Trail**
28. Check audit_logs table → contains entries for signup, login, update, delete (query via MySQL CLI: `SELECT * FROM audit_logs WHERE user_id = ?`)
29. Query: `SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 10` → shows recent changes

**Performance (Local)**
30. First profile fetch (cache miss, Redis empty) → < 200ms (local MySQL query)
31. Second profile fetch (cache hit, Redis populated) → < 10ms (local Redis fetch)
32. Verify Redis hit: `redis-cli` → `KEYS users::*` → should contain user keys
