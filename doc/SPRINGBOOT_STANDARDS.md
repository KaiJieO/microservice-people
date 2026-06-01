# Java Spring Boot Microservices — Standard Guidelines

**Purpose:** Ensure all Java Spring Boot microservices are built consistently, maintainable, and follow industry best practices. Use this as reference for all Spring Boot projects.

**Apply to:** qr-microservice, people-microservice, and all future Spring Boot backend projects.

---

## Project Structure

```
microservice-{name}/
├── build.gradle.kts           (Gradle build config)
├── settings.gradle.kts        (Gradle settings)
├── gradle/wrapper/            (Gradle wrapper)
├── Dockerfile                 (Containerization)
├── .dockerignore
├── .gitignore
├── PLAN.md                    (Architecture + phases)
├── TABLES.md                  (Database schema)
├── CLAUDE.md                  (Project constraints)
├── SPRINGBOOT_STANDARDS.md    (This file, copied from standard)
├── src/
│   ├── main/java/com/{org}/{project}/
│   │   ├── {ProjectName}Application.java     (Boot class)
│   │   ├── controller/                       (REST endpoints)
│   │   ├── service/                          (Business logic)
│   │   ├── repository/                       (Data access, JPA interfaces)
│   │   ├── entity/                           (JPA entities)
│   │   ├── dto/                              (Request/Response DTOs)
│   │   ├── exception/                        (Custom exceptions)
│   │   ├── security/                         (JWT, auth, RBAC)
│   │   ├── config/                           (Spring configurations)
│   │   └── util/                             (Utilities, helpers)
│   ├── main/resources/
│   │   ├── application.properties            (Config, not YAML)
│   │   └── db/migration/                     (Flyway SQL migrations)
│   └── test/java/com/{org}/{project}/
│       ├── controller/
│       ├── service/
│       └── integration/
```

---

## Build Configuration (build.gradle.kts)

### Spring Boot Version
```kotlin
id("org.springframework.boot") version "3.2.0"
id("io.spring.dependency-management") version "1.1.4"
```

### Core Dependencies (Always Include)
```kotlin
// Web + REST
implementation("org.springframework.boot:spring-boot-starter-web")

// Data Access
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("com.mysql:mysql-connector-j:8.2.0")
implementation("org.flywaydb:flyway-core:10.4.1")
implementation("org.flywaydb:flyway-mysql:10.4.1")

// Validation
implementation("org.springframework.boot:spring-boot-starter-validation")

// Caching (Redis)
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("redis.clients:jedis")

// JSON
implementation("com.fasterxml.jackson.core:jackson-databind")

// Lombok (reduce boilerplate)
compileOnly("org.projectlombok:lombok")
annotationProcessor("org.projectlombok:lombok")

// Testing
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

### Java Version
```kotlin
java.sourceCompatibility = JavaVersion.VERSION_17
```

### Main Class
```kotlin
springBoot { mainClass.set("com.{org}.{project}.{ProjectName}Application") }
```

---

## application.properties Configuration

**Format:** Use `.properties`, NOT `.yml` (consistency)

### Mandatory Settings
```properties
# Application metadata
spring.application.name={microservice-name}
server.port=808{X}  # Unique per microservice (8080, 8081, 8082, etc.)

# MySQL configuration (local or RDS)
spring.datasource.url=jdbc:mysql://localhost:3306/{database_name}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=test123
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Flyway (database migrations)
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true

# Redis caching
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.timeout=2000

# Cache settings
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000  # 1 hour
spring.cache.redis.use-key-prefix=true

# Logging
logging.level.com.{org}.{project}=DEBUG
logging.level.org.springframework.web=INFO
```

---

## Database Schema Standards

### Mandatory for All Microservices
1. **IDs:** UUID (VARCHAR(36)) as PRIMARY KEY, never auto-increment int
2. **Timestamps:** `created_at` (immutable), `updated_at` (on change)
3. **Soft Delete:** `status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')` instead of hard DELETE
4. **Audit Table:** Every microservice has `audit_logs` (action, table_name, record_id, old_values, new_values, created_at)
5. **Indexing:** Index all FK, all WHERE clauses (status, created_at, etc.)
6. **Migrations:** Flyway sequential (V1__..., V2__..., etc.), append-only, never reset

### Soft Delete Pattern
```sql
-- Query: always exclude deleted
SELECT * FROM {table} WHERE status != 'DELETED';

-- Delete: mark as deleted, never DROP
UPDATE {table} SET status = 'DELETED', updated_at = NOW() WHERE id = ?;

-- Recovery: reactivate if needed
UPDATE {table} SET status = 'ACTIVE' WHERE id = ?;
```

### Audit Logging Pattern
```sql
INSERT INTO audit_logs (user_id, action, table_name, record_id, old_values, new_values, created_at)
VALUES (?, 'CREATE'|'UPDATE'|'DELETE'|'LOGIN', ?, ?, JSON_OBJECT(...), JSON_OBJECT(...), NOW());
```

---

## JPA Entity Standards

### Lombok Annotations
```java
@Entity
@Table(name = "{table_name}")
@Data                          // getter/setter/toString/equals/hashCode
@NoArgsConstructor             // zero-arg constructor (JPA requirement)
@AllArgsConstructor            // full constructor
public class {EntityName} {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)   // generator required for DDL
    private String id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false,
            columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false,
            columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
}
```

### Timestamp Rule (MySQL 8)
- Map `LocalDateTime` to plain `datetime` (seconds) via `columnDefinition`, NOT the Hibernate default `datetime(6)`.
- Do NOT use `@ColumnDefault("CURRENT_TIMESTAMP")`: on a `datetime(6)` column its precision-0 default is rejected by MySQL as `Invalid default value`. Setting type + default in `columnDefinition` avoids the mismatch.
- `@Id String` needs `@GeneratedValue(strategy = GenerationType.UUID)` — without a generator Hibernate cannot create the table.
```

### Naming Conventions
- **Entity class:** `UserCredentials.java` (PascalCase, singular)
- **Table name:** `user_credentials` (snake_case)
- **Column names:** `email`, `phone`, `status` (snake_case, lowercase)
- **Enum values:** `ACTIVE`, `INACTIVE`, `DELETED` (UPPERCASE)

---

## DTO Standards

### Naming Conventions
- **Request DTO:** `{Action}Request.java` (e.g., LoginRequest, UpdateProfileRequest)
- **Response DTO:** `{Entity}Response.java` (e.g., UserResponse, UserProfileResponse)
- **Error DTO:** `ErrorResponse.java`

### Structure
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    // NO password_hash, NO sensitive data
}
```

### Never Expose in Response
- Password hashes
- API keys, tokens
- Internal IDs (except primary)
- PII (NRIC, SSN) unless explicitly needed

---

## Service Layer Standards

### Single Responsibility
```java
// UserService → handles user CRUD, profile, queries
// PasswordService → handles password hashing, reset flow
// AuthService → handles signup, login, JWT token generation
// AuditService → handles all audit logging
```

### Transactionality
```java
@Service
public class UserService {
    @Transactional  // Wrap write operations
    public User createUser(SignupRequest req) {
        // ...
    }
    
    // Read-only operations: no @Transactional needed
    public User getUserById(String id) {
        // ...
    }
}
```

### Validation
```java
@Service
public class UserService {
    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new InvalidPasswordException("Min 8 chars");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException("Needs uppercase");
        }
        // ... 1 upper, 1 lower, 1 digit, 1 symbol
    }
}
```

---

## Controller Standards

### Path Structure
```
/api/{resource}                    GET (list)
/api/{resource}                    POST (create)
/api/{resource}/{id}               GET (read)
/api/{resource}/{id}               PUT (update)
/api/{resource}/{id}               DELETE (soft delete)
/api/{service}/{action}            POST (custom actions)
```

### Example
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public UserResponse getUserById(@PathVariable String id) { }
    
    @PostMapping
    public UserResponse createUser(@Valid @RequestBody SignupRequest req) { }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public UserProfileResponse updateProfile(@PathVariable String id, 
                                    @Valid @RequestBody UpdateProfileRequest req) { }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(@PathVariable String id) { }
}
```

### Pagination
```java
@GetMapping
public Page<UserResponse> listUsers(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "created_at") String sortBy) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
    return userService.findAll(pageable);
}
```

---

## Exception Handling Standards

### Custom Exception Hierarchy
```java
// All extend RuntimeException
public class UserAlreadyExistsException extends RuntimeException { }
public class UserNotFoundException extends RuntimeException { }
public class InvalidPasswordException extends RuntimeException { }
public class AuthenticationFailedException extends RuntimeException { }
public class UnauthorizedAccessException extends RuntimeException { }
public class InvalidResetTokenException extends RuntimeException { }
```

### GlobalExceptionHandler
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserExists(UserAlreadyExistsException e) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse(409, "USER_EXISTS", e.getMessage()));
    }
    // ... other exceptions
}
```

### ErrorResponse DTO
```java
@Data
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();
    private String path;
}
```

### HTTP Status Mapping
| Exception | HTTP | Reason |
|---|---|---|
| UserAlreadyExistsException | 409 Conflict | Resource duplicate |
| UserNotFoundException | 404 Not Found | Resource missing |
| InvalidPasswordException | 400 Bad Request | Invalid input |
| AuthenticationFailedException | 401 Unauthorized | Wrong credentials |
| UnauthorizedAccessException | 403 Forbidden | Insufficient permissions |
| ValidationException | 400 Bad Request | Input validation failed |

---

## Authentication & Authorization Standards

### Password Hashing
```java
// Use Spring Security PasswordEncoder (BCrypt)
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // 12 cost factor
    }
}
```

### Password Requirements
```
Minimum 8 characters
At least 1 uppercase letter (A-Z)
At least 1 lowercase letter (a-z)
At least 1 numeric digit (0-9)
At least 1 special symbol (!@#$%^&*)
```

### JWT Token Standards
```java
@Component
public class JwtTokenProvider {
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours
    
    public String generateToken(User user) {
        return Jwts.builder()
            .setSubject(user.getId())
            .claim("email", user.getEmail())
            .claim("roles", user.getRoles())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(SignatureAlgorithm.HS512, secretKey)
            .compact();
    }
    
    public boolean validateToken(String token) { ... }
    public String getUserIdFromToken(String token) { ... }
}
```

### RBAC (Role-Based Access Control)
```java
// Roles: USER (default), ADMIN
@PreAuthorize("hasRole('ADMIN')")
public List<UserResponse> getAllUsers() { }

@PreAuthorize("hasRole('USER')")
public UserResponse getUserById(String id) {
    // Allow if id == currentUser.id OR currentUser.isAdmin()
}
```

### Service-to-Service Authentication (Phase 1)
```
Header: X-Service-Key: {shared-secret}
Middleware validates key, logs access in audit_logs
```

---

## Caching Standards

### Redis Setup
```properties
spring.redis.host=localhost
spring.redis.port=6379
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000  # 1 hour TTL
spring.cache.redis.use-key-prefix=true
```

### CacheConfig
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

### Cache Usage
```java
@Service
public class UserService {
    @Cacheable(value = "users", key = "#id")
    public User getUserById(String id) {
        // First call: miss → fetch from DB → store in Redis
        // Second call: hit → return from Redis (no DB query)
    }
    
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public void updateProfile(String userId, UpdateProfileRequest req) {
        // Update DB, then evict cache so next fetch is fresh
    }
}
```

### Cache Key Strategy
- **Format:** `{entity}::{identifier}` (e.g., `users::123`, `users::page:0:size:20`)
- **Avoid:** PII in keys, sensitive data, API tokens
- **TTL:** 1 hour passive expiry + manual evict on write

---

## Audit Logging Standards

### AuditService
```java
@Service
public class AuditService {
    public void log(String userId, String action, String tableName, 
                    String recordId, Object oldValues, Object newValues) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action);  // CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAILED
        entry.setTableName(tableName);
        entry.setRecordId(recordId);
        entry.setOldValues(oldValues);
        entry.setNewValues(newValues);
        entry.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(entry);
    }
}
```

### Actions to Log
- CREATE: New user/entity created
- UPDATE: Entity field changed
- DELETE: Soft delete (status = DELETED)
- LOGIN: Successful login
- LOGIN_FAILED: Failed login attempt
- PASSWORD_RESET: Password changed

### Retention
- Permanent (no cleanup, legal requirement)
- Queryable by user_id, table_name, action, created_at
- Include old_values + new_values for compliance

---

## Testing Standards

### Unit Tests (Services)
```java
@SpringBootTest
class UserServiceTest {
    @Test
    void testCreateUserSuccess() {
        SignupRequest req = new SignupRequest("user@test.com", "Test@123");
        User user = userService.createUser(req);
        assertNotNull(user.getId());
        assertEquals("user@test.com", user.getEmail());
    }
    
    @Test
    void testCreateUserDuplicate() {
        assertThrows(UserAlreadyExistsException.class, 
            () -> userService.createUser(existingEmail));
    }
}
```

### Integration Tests (API)
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest {
    @Test
    void testSignupEndpoint() {
        SignupRequest req = new SignupRequest(...);
        ResponseEntity<UserResponse> res = restTemplate.postForEntity(
            "/api/auth/signup", req, UserResponse.class);
        assertEquals(201, res.getStatusCodeValue());
    }
}
```

### E2E Tests
- Full signup → login → profile → password reset flow
- Test happy path + error scenarios
- Verify database state + cache state

### Coverage Target
- Services: > 80%
- Controllers: > 70%
- Overall: > 75%

---

## Code Quality Standards

### DRY Principle
- No code duplication
- Extract common logic to utility classes
- Reuse validation, error handling, logging

### Comments
- Only when WHY is non-obvious
- No comments explaining WHAT the code does (self-documenting)
- No comments referencing past issues or callers (belongs in git history)

### Naming Conventions
- **Classes:** PascalCase (UserCredentials, AuthService)
- **Methods:** camelCase (createUser, validatePassword)
- **Constants:** UPPER_SNAKE_CASE (MAX_PASSWORD_LENGTH)
- **Variables:** camelCase (userId, passwordHash)

### No Premature Optimization
- 3 similar lines → refactor
- 1-2 lines → leave it
- No abstractions for hypothetical future use
- No design patterns unless needed now

---

## Security Standards

### Input Validation
```java
@Valid @RequestBody SignupRequest req  // Validate at controller
String email = req.getEmail();
if (email == null || email.isBlank()) {
    throw new ValidationException("Email required");
}
```

### Password Security
```java
// Hash password before storing
String hashPassword = passwordEncoder.encode(plainPassword);
user.setPasswordHash(hashPassword);

// Validate against plain password
boolean isValid = passwordEncoder.matches(plainPassword, hashPassword);
```

### No Hardcoded Secrets
```java
// NEVER
private static final String API_KEY = "sk_live_123456";

// ALWAYS
private String apiKey = environment.getProperty("app.api.key");
```

### SQL Injection Prevention
```java
// GOOD: JPA parameterized queries
User user = userRepository.findByEmail(email);

// NEVER: String concatenation in native queries
Query q = em.createNativeQuery("SELECT * FROM users WHERE email = '" + email + "'");
```

### CSRF Protection
```java
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) {
        http.csrf().disable();  // For REST APIs (stateless)
    }
}
```

### Rate Limiting (Future)
- Implement per-endpoint rate limits
- Track by IP + user ID
- Return 429 Too Many Requests

---

## Deployment Standards

### Local Development (Phase 1)
```
MySQL: localhost:3306
Redis: localhost:6379
App: localhost:808{X}
```

### Production (Phase 2+)
```
Containerize: Docker image (Dockerfile provided)
Database: AWS RDS MySQL or managed MySQL
Cache: AWS ElastiCache or managed Redis
API Gateway: AWS API Gateway or nginx
Monitoring: CloudWatch or Datadog
```

### Rollback Strategy
- Database migrations append-only (never rollback data)
- If code rollback needed: revert commit, redeploy
- If data corruption: restore from backup

---

## Logging Standards

### Log Levels
```
DEBUG  : Development info (entity creation, queries)
INFO   : Lifecycle events (app started, request received)
WARN   : Unusual but handled (duplicate email, expired token)
ERROR  : Exceptional (exceptions, stack traces)
```

### Format
```properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
logging.level.com.{org}.{project}=DEBUG
logging.level.org.springframework=INFO
```

### Never Log
- Password hashes, API keys, tokens
- Full NRIC/SSN/PII (log only last 4 digits if needed)
- Credit card numbers

---

## Dependency Management

### Keep Updated
- Check `gradle dependencyUpdates` monthly
- Update Spring Boot 3.2.x patches (3.2.0 → 3.2.1)
- Be careful with major version upgrades (3.2 → 3.3)

### Avoid
- Unused dependencies
- Too many transitive dependencies
- Version conflicts (let Gradle resolve)

---

## Git & Versioning

### Version Format
```
1.0.0  (major.minor.patch)
```

### Commit Message Format (Conventional Commits)
```
feat: add user signup endpoint
fix: correct password validation regex
docs: update PLAN.md
refactor: extract password validation to utility
test: add unit tests for AuthService
```

### Branch Naming
```
feature/user-signup
fix/password-reset-bug
docs/api-docs
```

---

## Checklist Before Shipping

```
✓ All tests passing (unit + integration + E2E)
✓ Code reviewed + approved
✓ Security audit passed (no exposed secrets, SQL injection, XSS)
✓ Documentation updated (PLAN.md, README, code comments)
✓ No hardcoded credentials
✓ Error handling comprehensive
✓ Logging configured
✓ Performance acceptable (< 200ms for cache misses)
✓ Database migrations tested
✓ Audit logging working
✓ Caching strategy verified
```

---

## Reference Documents

- **PLAN.md** — Architecture, phases, endpoints, decisions
- **TABLES.md** — Database schema, indexes, constraints
- **CLAUDE.md** — Project constraints, scope boundaries
- **SPRINGBOOT_STANDARDS.md** — This file (reusable across projects)

---

**Last Updated:** 2026-05-30

**Version:** 1.0

**Applies To:** All Java Spring Boot microservices (qr-microservice, people-microservice, future projects)