# PHASE 2: Auth + Services

**Timeline:** 4-5 days
**Owner:** You
**Status:** READY TO START
**Depends on:** Phase 1 complete (entities, repositories, migrations, Flyway+validate confirmed)

---

## What

Build the service + security layer. No controllers yet (Phase 3). Deliverables:

1. 9 DTOs (request/response objects)
2. 7 exception classes (6 custom + GlobalExceptionHandler)
3. JWT security layer (JwtTokenProvider + SecurityConfig + JwtAuthenticationFilter)
4. 5 services (AuthService, PasswordService, UserService, SessionService, AuditService)
5. Pre-work fixes (Phase 1 items that must be applied first)

---

## UUID Rule (All IDs)

**Every primary key and every foreign key reference is a UUID (`VARCHAR(36)`).**

- All `id` fields: `@Id @GeneratedValue(strategy = GenerationType.UUID) @Column(length = 36)`
- All FK fields (e.g. `userId`, `emailId`): `@Column(nullable = false, length = 36)`
- No auto-increment integers anywhere in this service
- DTOs expose `id` fields as `String` (UUID string representation)

---

## Pre-work Fixes (Phase 1 — apply before Phase 2 code)

### 1. Rename table: `user_signup` → `user_credentials` ✅ DONE

Applied across migrations, entity, repository, service, and docs. Final state:
- Migration file `V1__Create_User_Credentials.sql`, table `user_credentials`
- V2–V5 FKs reference `user_credentials(id)`
- Entity `UserCredentials.java` with `@Table(name = "user_credentials")`
- All repo/service references updated

### 2. Fix Redis properties (application.properties)
`spring.redis.*` removed in Spring Boot 4.x:
```properties
# Remove these:
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.timeout=2000

# Replace with:
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000
```

### 3. Add `@EnableCaching` to `CacheConfig`
```java
@Configuration
@EnableCaching   // without this, @Cacheable/@CacheEvict silently ignored
public class CacheConfig {
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.create(factory);
    }
}
```

### 4. Fix `PasswordResetTokenRepository`
Change `findByUserId` return type + add `deleteByUserId`:
```java
List<PasswordResetToken> findByUserId(String userId);
void deleteByUserId(String userId);
```

### 5. Add missing `UserCredentialsRepository` methods
```java
boolean existsByEmail(String email);
boolean existsByPhone(String phone);
Optional<UserCredentials> findByEmailAndStatusNot(String email, UserCredentials.Status status);
Optional<UserCredentials> findByIdAndStatusNot(String id, UserCredentials.Status status);
Page<UserCredentials> findByStatusNot(UserCredentials.Status status, Pageable pageable);
```

### 6. Update `PasswordResetToken` entity + migration
Replace `email VARCHAR(255)` column with `emailId VARCHAR(36)` UUID FK to `user_credentials`:

**Entity change:**
```java
// Remove:
@Column(nullable = false, length = 255)
private String email;

// Add:
@Column(nullable = false, length = 36, name = "email_id")
private String emailId;   // UUID FK → user_credentials.id (same as userId; kept for explicit email-reset traceability)
```

**V3 migration update** (or new V7 if append-only):
```sql
ALTER TABLE password_reset_tokens
  DROP COLUMN email,
  ADD COLUMN email_id VARCHAR(36) NOT NULL AFTER user_id;
```

### 7. Add `emailVerifiedAt` + `phoneVerifiedAt` to `user_credentials`
New columns in the credentials table (timestamp of verification events):

**Entity addition:**
```java
@Column(name = "email_verified_at", columnDefinition = "datetime")
private LocalDateTime emailVerifiedAt;

@Column(name = "phone_verified_at", columnDefinition = "datetime")
private LocalDateTime phoneVerifiedAt;
```

**New migration `V6__Add_Verification_Timestamps.sql`** (or include in Option A above):
```sql
ALTER TABLE user_credentials
  ADD COLUMN email_verified_at DATETIME NULL AFTER email_verified,
  ADD COLUMN phone_verified_at DATETIME NULL AFTER phone_verified;
```

### 8. Add JWT config to `application.properties`
```properties
# JWT
app.jwt.secret=your-256-bit-secret-key-minimum-32-chars-replace-in-prod
app.jwt.expiration-ms=86400000
app.jwt.cookie-name=AUTH_TOKEN

# BCrypt
app.security.bcrypt-strength=12
```

---

## Token Hashing Strategy

| Token type | Hash algorithm | Reason |
|---|---|---|
| Password (stored in DB) | **BCrypt** | Slow by design; brute-force resistant; passwords need work factor |
| Password reset token | **SHA-256** | Deterministic → lookupable by `findByTokenHash`; token is random UUID (entropy already high, salt not needed) |
| Session token (JWT hash) | **SHA-256** | Same reason; BCrypt truncates at 72 bytes (JWT >> 72 bytes → collisions) |

**BCrypt** — use for passwords only. `new BCryptPasswordEncoder(12)`.  
**SHA-256** — use for all tokens. `MessageDigest.getInstance("SHA-256")` → hex string.

---

## Dependencies to Add (`build.gradle.kts`)

```kotlin
// JWT
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

**Do NOT add jedis.** `spring-boot-starter-session-data-redis` uses Lettuce by default.

> ⚠️ **Pre-Phase 5 note:** Several test starters in `build.gradle.kts` do not exist as real artifacts
> (`spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`,
> `spring-boot-starter-webmvc-test`, etc.). Replace all with `spring-boot-starter-test`
> before writing tests in Phase 5.

---

## Code Structure

```
src/main/java/com/microservice/people/
├── dto/
│   ├── SignupRequest.java
│   ├── LoginRequest.java
│   ├── UserResponse.java
│   ├── UpdateProfileRequest.java
│   ├── PasswordResetRequest.java
│   ├── PasswordResetConfirmRequest.java
│   ├── ChangePasswordRequest.java
│   ├── SessionResponse.java
│   └── ErrorResponse.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── UserAlreadyExistsException.java
│   ├── UserNotFoundException.java
│   ├── InvalidPasswordException.java
│   ├── AuthenticationFailedException.java
│   ├── InvalidResetTokenException.java
│   └── UnauthorizedAccessException.java
├── security/
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   └── SecurityConfig.java
└── service/
    ├── AuthService.java
    ├── PasswordService.java
    ├── UserService.java
    ├── SessionService.java
    └── AuditService.java
```

---

## DTOs

All DTOs: `@Data @NoArgsConstructor @AllArgsConstructor` (Lombok). Validation: `jakarta.validation.constraints.*`.

### SignupRequest
```java
@NotBlank @Email @Size(max = 255)
String email;

@NotBlank @Pattern(regexp = "^\\+?[0-9]{8,20}$")
String phone;

@NotBlank
String password;   // complexity validated in PasswordService
```

### LoginRequest
```java
@NotBlank @Email
String email;

@NotBlank
String password;
```

### UserResponse
Single response DTO for `user_credentials` table. No personal details — those are a separate entity/endpoint.

```java
String id;               // UUID
String email;
String phone;
String roles;
String status;
Boolean emailVerified;
Boolean phoneVerified;
LocalDateTime emailVerifiedAt;
LocalDateTime phoneVerifiedAt;
LocalDateTime createdAt;
```

### UpdateProfileRequest
Maps to `UserProfile` fields. All optional (user fills profile post-signup).

```java
@Size(max = 50)  String salutation;
@Size(max = 100) String firstName;
@Size(max = 100) String lastName;
LocalDate dateOfBirth;
String gender;           // M or F
String identityType;     // NRIC or PASSPORT
@Size(max = 20) String identityNumber;
String citizenship;      // MALAYSIAN or FOREIGNER
@Size(max = 100) String nationality;
@Size(max = 255) String address1;
@Size(max = 255) String address2;
@Size(max = 255) String address3;
@Size(max = 100) String city;
String state;            // 13 Malaysian states enum value
@Size(max = 10)  String postcode;
```

Email / phone / roles / status excluded — admin-only paths, not self-update.

### PasswordResetRequest (for `POST /api/passwords/reset-request`)
```java
@Email @NotBlank
String email;
```

### PasswordResetConfirmRequest (for `POST /api/passwords/reset`)
```java
@NotBlank
String token;

@NotBlank
String newPassword;
```

### ChangePasswordRequest (for `PUT /api/passwords/{id}`)
```java
@NotBlank
String oldPassword;

@NotBlank
String newPassword;
```

### SessionResponse (never expose `tokenHash`)
```java
String id;               // UUID
String userId;           // UUID FK → user_credentials
String ipAddress;
String userAgent;
LocalDateTime expiresAt;
LocalDateTime createdAt;
LocalDateTime updatedAt;
```

### ErrorResponse
```java
int status;
String error;
String message;
LocalDateTime timestamp;
String path;
```

---

## Exceptions

All extend `RuntimeException`. Constructor: `super(message)`. HTTP mapping in `GlobalExceptionHandler`.

| Class | HTTP | Default Message |
|---|---|---|
| `UserAlreadyExistsException` | 409 | "User with this email or phone already exists" |
| `UserNotFoundException` | 404 | "User not found" |
| `InvalidPasswordException` | 400 | "Password does not meet requirements" |
| `AuthenticationFailedException` | 401 | "Invalid email or password" |
| `InvalidResetTokenException` | 400 | "Reset token is invalid, expired, or already used" |
| `UnauthorizedAccessException` | 403 | "You do not have permission to access this resource" |

### GlobalExceptionHandler
`@RestControllerAdvice`. One `@ExceptionHandler` per class → `ResponseEntity<ErrorResponse>`.

- Each custom exception → mapped HTTP status
- `MethodArgumentNotValidException` → 400, aggregate field errors into `message`
- `AccessDeniedException` (Spring Security) → 403
- `Exception` (catch-all) → 500

Inject `HttpServletRequest` to populate `ErrorResponse.path`.

---

## Security Layer

### JwtTokenProvider
`@Component`. Reads `app.jwt.secret` and `app.jwt.expiration-ms` via `@Value`.

**Use JJWT 0.12.x API** (standards doc has stale 0.11 API — do not copy from there):

```java
// Generate
String generateToken(UserCredentials user) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder()
        .subject(user.getId())
        .claim("email", user.getEmail())
        .claim("roles", user.getRoles())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expirationMs))
        .signWith(key)
        .compact();
}

// Validate
boolean validateToken(String token) {
    try {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return true;
    } catch (JwtException | IllegalArgumentException e) {
        return false;
    }
}

// Extract claims
String getUserId(String token)          // → subject (UUID)
String getEmail(String token)           // → claim "email"
List<String> getRoles(String token)     // → claim "roles" split by ","
```

Secret must be ≥ 32 bytes for HS256.

### JwtAuthenticationFilter
`extends OncePerRequestFilter`. Reads JWT from HTTP-only cookie named `app.jwt.cookie-name`.

Flow:
1. Extract cookie value
2. `jwtTokenProvider.validateToken(token)` → invalid: `chain.doFilter()` and return
3. Build `UsernamePasswordAuthenticationToken` with authorities:
   `getRoles(token)` → each prefixed `"ROLE_"` → `new SimpleGrantedAuthority("ROLE_" + role)`
4. Set on `SecurityContextHolder`
5. `chain.doFilter()`

### SecurityConfig
`@Configuration @EnableWebSecurity @EnableMethodSecurity`

**Spring Security 7 — no `WebSecurityConfigurerAdapter` (removed). Lambda DSL only.**
`@EnableMethodSecurity` replaces removed `@EnableGlobalMethodSecurity`.

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/auth/**",
                "/api/passwords/reset-request",
                "/api/passwords/reset"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
}

@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

---

## Services

### AuditService
`@Service`. Method-level `@Transactional(propagation = Propagation.REQUIRES_NEW)` — audit writes survive business transaction rollback.

```java
void log(
    String userId,      // UUID, nullable (e.g. LOGIN_FAILED before user found)
    String action,      // constant: CREATE | UPDATE | DELETE | LOGIN | LOGIN_FAILED
                        //           PASSWORD_RESET | PASSWORD_RESET_REQUEST | LOGOUT | LOGOUT_ALL
    String tableName,
    String recordId,    // UUID of affected row
    Object oldValues,   // serialized to JSON String via ObjectMapper (null for CREATE)
    Object newValues,   // serialized to JSON String via ObjectMapper (null for DELETE)
    String ipAddress,
    String userAgent
)
```

Jackson `ObjectMapper.writeValueAsString()` for old/new values. Saves `AuditLog` via `AuditLogRepository.save()`.

---

### AuthService
`@Service @Transactional` (on writes).

**`signup(SignupRequest req, String ip, String ua) → UserResponse`**
1. `existsByEmail(email)` → throw `UserAlreadyExistsException`
2. `existsByPhone(phone)` → throw `UserAlreadyExistsException`
3. `passwordService.validate(req.password)`
4. BCrypt encode password
5. Save `UserCredentials` (roles="USER", status=ACTIVE, emailVerified=false, phoneVerified=false)
6. `auditService.log(userId, "CREATE", "user_credentials", userId, null, userResponse, ip, ua)`
7. Return `UserResponse`

> No `UserProfile` created at signup. Profile filled separately post-signup.

**`login(LoginRequest req, String ip, String ua) → String (JWT)`**
1. `findByEmailAndStatusNot(email, DELETED)` → absent: audit `LOGIN_FAILED`, throw `AuthenticationFailedException`
2. Check `lockedUntil` (not null AND after now) → audit `LOGIN_FAILED`, throw `AuthenticationFailedException`
3. `passwordEncoder.matches(password, user.passwordHash)`:
   - **Fail:** increment `loginAttemptCount`; if ≥ 5 set `lockedUntil = now() + 15min`; save; audit `LOGIN_FAILED`; throw `AuthenticationFailedException`
   - **Success:** reset `loginAttemptCount = 0`, set `lastLoginAt = now()`; save
4. Generate JWT via `jwtTokenProvider.generateToken(user)`
5. SHA-256 hash the JWT → store as `tokenHash`
6. Save `UserSession` (userId, tokenHash, ip, ua, expiresAt = now()+24h)
7. Audit `LOGIN` on `user_sessions`
8. Return JWT (controller sets HTTP-only cookie in Phase 3)

---

### PasswordService
`@Service @Transactional` (on writes).

**`validate(String password)` — void, throws `InvalidPasswordException`**
- Length ≥ 8
- At least 1 uppercase (A-Z)
- At least 1 lowercase (a-z)
- At least 1 digit (0-9)
- At least 1 special character (`!@#$%^&*`)

Throw with specific failure message.

**`requestReset(String email, String ip, String ua)`**
1. `findByEmailAndStatusNot(email, DELETED)` → not found: **silently return** (no user enumeration)
2. `deleteByUserId(userId)` — invalidate any prior unused tokens
3. Generate random UUID token (plaintext)
4. SHA-256 hash → store as `tokenHash`
5. Save `PasswordResetToken` (userId, emailId=userId, tokenHash, expiresAt = now()+5min, usedAt=null)
6. Log to console: `log.info("Password reset token for {}: {}", email, token)` (no email service Phase 2)
7. Audit `PASSWORD_RESET_REQUEST` on `password_reset_tokens`

**`performReset(String token, String newPassword, String ip, String ua)`**
1. SHA-256 hash the incoming `token`
2. `findByTokenHash(hash)` → absent: throw `InvalidResetTokenException`
3. Validate: `expiresAt` after now AND `usedAt == null` → else throw `InvalidResetTokenException`
4. `validate(newPassword)`
5. BCrypt encode new password; update `UserCredentials.passwordHash`; save
6. Set `usedAt = now()`; save token
7. Evict user cache: `cacheManager.getCache("users").evict(userId)`
8. Audit `PASSWORD_RESET` on `user_credentials`

**`changePassword(String userId, String oldPassword, String newPassword, String ip, String ua)`**
1. `findByIdAndStatusNot(userId, DELETED)` → throw `UserNotFoundException`
2. `passwordEncoder.matches(oldPassword, hash)` → fail: throw `AuthenticationFailedException`
3. `validate(newPassword)`
4. BCrypt encode; update `passwordHash`; save
5. `cacheManager.getCache("users").evict(userId)`
6. Audit `UPDATE` on `user_credentials` (old_values: `{passwordChanged: true}`, new_values: `{passwordChanged: true}`)

---

### UserService
`@Service`.

**`getUserById(String id) → UserResponse`**
`@Cacheable(value="users", key="#id")`
1. `findByIdAndStatusNot(id, DELETED)` → throw `UserNotFoundException`
2. Map `UserCredentials` → `UserResponse`

**`getAllUsers(Pageable pageable, Status statusFilter) → Page<UserResponse>`**
`@PreAuthorize("hasRole('ADMIN')")`
1. `findByStatusNot(DELETED, pageable)` (optional status filter via `@Query` if provided)
2. Map each to `UserResponse`

No `@Cacheable` on list — pagination key explosion risk.

**`updateProfile(String id, UpdateProfileRequest req, String ip, String ua) → UserProfileResponse`**
`@CacheEvict(value="users", key="#id") @Transactional`
1. `findByIdAndStatusNot(id, DELETED)` → throw `UserNotFoundException`
2. Load `UserProfile` via `findByUserId(id)` (create if absent — first profile update)
3. Capture old state for audit JSON
4. Apply non-null fields from `req` to `UserProfile`
5. Save `UserProfile`
6. Audit `UPDATE` on `user_profile` with old/new JSON
7. Map `UserCredentials` → `UserResponse`

**`deleteUser(String id, String ip, String ua)`**
`@CacheEvict(value="users", key="#id") @Transactional @PreAuthorize("hasRole('ADMIN')")`
1. `findByIdAndStatusNot(id, DELETED)` → throw `UserNotFoundException`
2. Set `status = DELETED`; save
3. Audit `DELETE` on `user_credentials`

**`updateEmail(String id, String newEmail, String ip, String ua)`**
`@CacheEvict(value="users", key="#id") @Transactional @PreAuthorize("hasRole('ADMIN')")`
1. `existsByEmail(newEmail)` → throw `UserAlreadyExistsException`
2. Update email; save
3. Audit `UPDATE` on `user_credentials`

---

### SessionService
`@Service`.

**`getSession(String id) → SessionResponse`**
`findById(id)` → throw `UserNotFoundException` if absent. Map to `SessionResponse`.

**`getUserSessions(String userId) → List<SessionResponse>`**
`findByUserId(userId)` → map each to `SessionResponse`.

**`deleteSession(String id, String ip, String ua)` — `@Transactional`**
1. Load session; delete
2. Audit `LOGOUT` on `user_sessions`

**`deleteAllSessions(String userId, String ip, String ua)` — `@Transactional`**
1. `deleteByUserId(userId)`
2. Audit `LOGOUT_ALL` on `user_sessions`

---

## Security Considerations

1. **No user enumeration** — `requestReset` always returns success even if email not found
2. **Brute force protection** — lock after 5 failed login attempts (15 min, `lockedUntil`)
3. **Token one-time use** — `usedAt` set on `performReset`; reject if non-null
4. **Password hash only** — raw password never logged or stored
5. **NRIC not in credentials** — `user_credentials` has no NRIC; personal details are separate entity
6. **HTTP-only cookie** — JWT set by controller in Phase 3 (not accessible to JS)
7. **BCrypt strength 12** — via `app.security.bcrypt-strength`
8. **Roles prefix** — `JwtAuthenticationFilter` adds `"ROLE_"` prefix; `@PreAuthorize("hasRole('ADMIN')")` resolves correctly
9. **SHA-256 for tokens** — deterministic, full-length, no 72-byte truncation
10. **`tokenHash` never in response** — `SessionResponse` excludes it

---

## Design Decisions (Pending Approval)

| Decision | Options | Recommendation |
|---|---|---|
| Citizenship `PR` value | Entity has MALAYSIAN/FOREIGNER only; PLAN.md lists PR | Add `PR` to enum + V7 migration OR remove from PLAN |
| Admin pagination citizenship filter | `@Query` join vs JPA Specifications | `@Query` join (simpler, defer Specs to Phase 4+) |
| `@Async` for AuditService | Async (non-blocking) vs Sync `REQUIRES_NEW` | Sync Phase 2 (simpler, no thread pool config) |
| `emailId` in `PasswordResetToken` | UUID FK same as `userId` — redundant? | Keep for explicit email-reset audit trail |

---

## Code Review Checklist

- [ ] All DTOs: `@Data @NoArgsConstructor @AllArgsConstructor`
- [ ] `GlobalExceptionHandler` covers 6 custom + `MethodArgumentNotValidException` + catch-all
- [ ] `JwtTokenProvider` uses JJWT 0.12.x API (not stale standards doc samples)
- [ ] `SecurityConfig` uses lambda DSL, `@EnableMethodSecurity`
- [ ] `AuditService` uses `REQUIRES_NEW` propagation
- [ ] Password reset token: SHA-256 hash stored (not BCrypt)
- [ ] Session tokenHash: SHA-256 (not BCrypt — avoids 72-byte truncation)
- [ ] `performReset` cache evict uses `userId` (not token string)
- [ ] No raw password in logs or audit JSON
- [ ] `requestReset` returns success regardless of email existence (no enumeration)
- [ ] Login lockout: `loginAttemptCount` increments, `lockedUntil` set after 5 failures
- [ ] BCrypt strength ≥ 12 for passwords
- [ ] JWT secret ≥ 32 bytes, externalized in `application.properties`
- [ ] `spring.data.redis.*` (not deprecated `spring.redis.*`)
- [ ] `@EnableCaching` on `CacheConfig`
- [ ] `SessionResponse` used — never return `UserSession` entity directly
- [ ] All IDs are UUID (`VARCHAR(36)`) — PKs and FKs

---

## Testing Checklist

### Unit Tests
- [ ] `PasswordService.validate()` — each requirement failure throws `InvalidPasswordException`
- [ ] `AuthService.signup()` — duplicate email/phone throws `UserAlreadyExistsException`
- [ ] `AuthService.login()` — 5 failures sets `lockedUntil`; success resets count
- [ ] `JwtTokenProvider` — generated token validates; expired token fails; claims correct
- [ ] `AuditService.log()` — saves `AuditLog` with correct fields

### Integration Tests
- [ ] Signup → creates `user_credentials` row only (no personal details row)
- [ ] Login → creates `user_sessions` row; returns JWT
- [ ] Login 5x wrong → `locked_until` set
- [ ] Full reset flow: `requestReset` → `performReset` → login with new password
- [ ] Expired token → `InvalidResetTokenException`
- [ ] Token used twice → `InvalidResetTokenException`
- [ ] Cache hit on `getUserById` second call (Redis must be running)

---

## Effort Estimate

| Task | Estimate |
|---|---|
| Pre-work fixes (rename + repo + config) | 60 min |
| DTOs (9 files) | 45 min |
| Exceptions (7 files) | 30 min |
| JwtTokenProvider + Filter + SecurityConfig | 90 min |
| AuditService | 30 min |
| AuthService | 60 min |
| PasswordService | 60 min |
| UserService | 60 min |
| SessionService | 30 min |
| Unit tests | 90 min |
| Integration tests | 90 min |
| **Total** | **~9.5 hours (2 working days)** |

---

## Blockers & Mitigations

| Blocker | Mitigation |
|---|---|
| Table rename breaks Flyway history | Use Option B (V6 RENAME TABLE) or Option A (drop+recreate, local only) |
| JWT secret too short | Must be ≥ 32 bytes; fail-fast on startup if shorter |
| BCrypt slow in unit tests | Use `BCryptPasswordEncoder(4)` in test profile |
| Redis not running | `spring.cache.type=none` in test profile |
| `spring-boot-starter-*-test` starters | Replace all with `spring-boot-starter-test` in Phase 5 |

---

## Next Phase

Once Phase 2 complete + all tests passing:
- **Phase 3:** Controllers + API (AuthController, UserController, PasswordController, SessionController)

---

## References

- **PLAN.md:** Endpoint definitions, JWT spec, password policy
- **TABLES.md:** Schema reference
- **SPRINGBOOT_STANDARDS.md:** Entity + service patterns
  - ⚠️ JWT samples use deprecated JJWT 0.11 API — use JJWT 0.12.x patterns from this doc
  - ⚠️ SecurityConfig shows removed `WebSecurityConfigurerAdapter` — use `SecurityFilterChain` bean
  - ⚠️ Redis config shows `spring.redis.*` — use `spring.data.redis.*` on Spring Boot 4.x
- **PHASE_1.md:** Entity layer reference

---

## Last Updated

2026-06-01 — Revised: user_credentials rename, SHA-256 token hashing, PasswordResetToken.emailId,
             merged UserResponse, removed signup→UserProfile coupling, SessionResponse DTO added
