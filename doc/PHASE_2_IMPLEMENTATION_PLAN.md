# Phase 2 Continuation: Exceptions + Security + Services

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase 2 — write 7 exception classes, 3 security classes, and 5 service classes, preceded by pre-work fixes to entities, repositories, migrations, and config.

**Architecture:** Pre-work renames `UserCredentials` → `UserCredentials` (entity + table + repos), updates `PasswordResetToken` to use `emailId` instead of `email`, and fixes Redis/JWT/CacheConfig. Then exceptions, security, and services are added following the fully-reviewed code in `doc/PHASE_2_CODE_IMPLEMENTATION_REVIEW.md`.

**Tech Stack:** Spring Boot 4.x, Spring Security 7, JJWT 0.12.x, BCrypt, SHA-256, Redis (Lettuce), Flyway, Lombok, Jakarta Validation

---

## File Map

**Modified:**
- `src/main/resources/db/migration/V1__Create_User_Credentials.sql` → rename table, add verification timestamp cols
- `src/main/resources/db/migration/V2__Create_User_Personal_Details.sql` → update FK ref
- `src/main/resources/db/migration/V3__Create_Password_Reset_Tokens.sql` → email→email_id col, update FK ref
- `src/main/resources/db/migration/V4__Create_User_Sessions.sql` → update FK ref
- `src/main/java/com/microservice/people/entity/UserCredentials.java` → rename to `UserCredentials.java`, add fields, update @Table
- `src/main/java/com/microservice/people/repository/UserCredentialsRepository.java` → rename to `UserCredentialsRepository.java`, add methods
- `src/main/java/com/microservice/people/entity/PasswordResetToken.java` → email→emailId
- `src/main/java/com/microservice/people/repository/PasswordResetTokenRepository.java` → fix return type + add deleteByUserId
- `src/main/java/com/microservice/people/config/CacheConfig.java` → add @EnableCaching
- `src/main/resources/application.properties` → fix Redis keys, add JWT config
- `build.gradle.kts` → add JWT deps, fix test starters

**Created:**
- `src/main/java/com/microservice/people/exception/` — 7 files
- `src/main/java/com/microservice/people/security/` — 3 files
- `src/main/java/com/microservice/people/service/` — 5 files

---

## Task 1: Update Flyway Migrations (Option A — local dev drop/recreate)

> All FK references currently point to `user_credentials`. We update all migrations in-place since this is local dev only.

**Files:**
- Modify: `src/main/resources/db/migration/V1__Create_User_Credentials.sql`
- Modify: `src/main/resources/db/migration/V2__Create_User_Personal_Details.sql`
- Modify: `src/main/resources/db/migration/V3__Create_Password_Reset_Tokens.sql`
- Modify: `src/main/resources/db/migration/V4__Create_User_Sessions.sql`

- [ ] **Step 1: Update V1 — rename table, add verification timestamp columns**

Replace entire `V1__Create_User_Credentials.sql` with:

```sql
CREATE TABLE user_credentials (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    roles VARCHAR(255) DEFAULT 'USER',
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED') DEFAULT 'ACTIVE',
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    email_verified_at DATETIME NULL,
    phone_verified_at DATETIME NULL,
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

- [ ] **Step 2: Update V2 — change FK ref from user_credentials to user_credentials**

Open `V2__Create_User_Personal_Details.sql`. Find the line:
```sql
FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
```
Change to:
```sql
FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
```

- [ ] **Step 3: Update V3 — replace email column with email_id, update FK**

Replace entire `V3__Create_Password_Reset_Tokens.sql` with:

```sql
CREATE TABLE password_reset_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    email_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_token_hash (token_hash),
    INDEX idx_expires_at (expires_at),
    INDEX idx_user_id (user_id)
);
```

- [ ] **Step 4: Update V4 — change FK ref from user_credentials to user_credentials**

Open `V4__Create_User_Sessions.sql`. Find the line:
```sql
FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
```
Change to:
```sql
FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
```

- [ ] **Step 5: Drop and recreate the database**

```bash
mysql -u root -ptest123 -e "DROP DATABASE IF EXISTS microservice_people_db; CREATE DATABASE microservice_people_db;"
```

Expected: no errors. Flyway will re-apply all 5 migrations on next bootRun.

---

## Task 2: Rename UserCredentials Entity → UserCredentials

**Files:**
- Delete: `src/main/java/com/microservice/people/entity/UserCredentials.java`
- Create: `src/main/java/com/microservice/people/entity/UserCredentials.java`

- [ ] **Step 1: Create UserCredentials.java**

```java
package com.microservice.people.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_credentials", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_phone", columnList = "phone"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 255)
    private String roles = "USER";

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')")
    private Status status = Status.ACTIVE;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean emailVerified = false;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean phoneVerified = false;

    @Column(name = "email_verified_at", columnDefinition = "datetime")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "phone_verified_at", columnDefinition = "datetime")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "last_login_at", columnDefinition = "datetime")
    private LocalDateTime lastLoginAt;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer loginAttemptCount = 0;

    @Column(name = "locked_until", columnDefinition = "datetime")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE, INACTIVE, SUSPENDED, DELETED
    }
}
```

- [ ] **Step 2: Delete old UserCredentials.java**

Delete the file `src/main/java/com/microservice/people/entity/UserCredentials.java`.

---

## Task 3: Rename UserCredentialsRepository → UserCredentialsRepository

**Files:**
- Delete: `src/main/java/com/microservice/people/repository/UserCredentialsRepository.java`
- Create: `src/main/java/com/microservice/people/repository/UserCredentialsRepository.java`

- [ ] **Step 1: Create UserCredentialsRepository.java**

```java
package com.microservice.people.repository;

import com.microservice.people.entity.UserCredentials;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, String> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Optional<UserCredentials> findByEmailAndStatusNot(String email, UserCredentials.Status status);
    Optional<UserCredentials> findByIdAndStatusNot(String id, UserCredentials.Status status);
    Page<UserCredentials> findByStatusNot(UserCredentials.Status status, Pageable pageable);
}
```

- [ ] **Step 2: Delete old UserCredentialsRepository.java**

Delete `src/main/java/com/microservice/people/repository/UserCredentialsRepository.java`.

---

## Task 4: Update PasswordResetToken Entity + Repository

**Files:**
- Modify: `src/main/java/com/microservice/people/entity/PasswordResetToken.java`
- Modify: `src/main/java/com/microservice/people/repository/PasswordResetTokenRepository.java`

- [ ] **Step 1: Replace PasswordResetToken.java — swap email field for emailId**

Replace entire file:

```java
package com.microservice.people.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens", indexes = {
    @Index(name = "idx_token_hash", columnList = "token_hash"),
    @Index(name = "idx_expires_at", columnList = "expires_at"),
    @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 36, name = "email_id")
    private String emailId;

    @Column(nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(nullable = false, name = "expires_at", columnDefinition = "datetime")
    private LocalDateTime expiresAt;

    @Column(name = "used_at", columnDefinition = "datetime")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Replace PasswordResetTokenRepository.java**

```java
package com.microservice.people.repository;

import com.microservice.people.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    List<PasswordResetToken> findByUserId(String userId);
    void deleteByUserId(String userId);
}
```

---

## Task 5: Config Fixes

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/microservice/people/config/CacheConfig.java`

- [ ] **Step 1: Add JWT dependencies to build.gradle.kts**

In the `dependencies` block, add after the last `implementation(...)` line:

```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

- [ ] **Step 2: Fix fake test starters in build.gradle.kts**

Replace the test dependencies block. The fake starters (`spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`, etc.) do not exist. Replace with:

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-starter-security")
testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
testCompileOnly("org.projectlombok:lombok")
testRuntimeOnly("org.junit.platform:junit-platform-launcher")
testAnnotationProcessor("org.projectlombok:lombok")
```

- [ ] **Step 3: Fix application.properties — Redis keys + add JWT/BCrypt config**

Replace the Redis block:
```properties
# Remove:
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.timeout=2000

# Replace with:
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000
```

Add at end of file:
```properties
# JWT
app.jwt.secret=microservice-people-secret-key-replace-in-production-env
app.jwt.expiration-ms=86400000
app.jwt.cookie-name=AUTH_TOKEN

# BCrypt
app.security.bcrypt-strength=12
```

- [ ] **Step 4: Add @EnableCaching to CacheConfig.java**

```java
package com.microservice.people.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        return RedisCacheManager.create(factory);
    }
}
```

- [ ] **Step 5: Verify Gradle resolves**

```bash
./gradlew dependencies --configuration compileClasspath 2>&1 | grep -E "jjwt|ERROR"
```

Expected: `io.jsonwebtoken:jjwt-api:0.12.6` present, no resolution errors.

- [ ] **Step 6: Commit pre-work**

```bash
git add -A
git commit -m "fix(pre-work): rename user_signup→user_credentials, fix redis/jwt config, fix test deps"
```

---

## Task 6: Exception Classes (7 files)

**Files:**
- Create: `src/main/java/com/microservice/people/exception/UserAlreadyExistsException.java`
- Create: `src/main/java/com/microservice/people/exception/UserNotFoundException.java`
- Create: `src/main/java/com/microservice/people/exception/InvalidPasswordException.java`
- Create: `src/main/java/com/microservice/people/exception/AuthenticationFailedException.java`
- Create: `src/main/java/com/microservice/people/exception/InvalidResetTokenException.java`
- Create: `src/main/java/com/microservice/people/exception/UnauthorizedAccessException.java`
- Create: `src/main/java/com/microservice/people/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create 6 exception classes**

`UserAlreadyExistsException.java`:
```java
package com.microservice.people.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
```

`UserNotFoundException.java`:
```java
package com.microservice.people.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

`InvalidPasswordException.java`:
```java
package com.microservice.people.exception;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
```

`AuthenticationFailedException.java`:
```java
package com.microservice.people.exception;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
```

`InvalidResetTokenException.java`:
```java
package com.microservice.people.exception;

public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException(String message) {
        super(message);
    }
}
```

`UnauthorizedAccessException.java`:
```java
package com.microservice.people.exception;

public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create GlobalExceptionHandler.java**

```java
package com.microservice.people.exception;

import com.microservice.people.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(new ErrorResponse(
            409, "UserAlreadyExistsException", ex.getMessage(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(new ErrorResponse(
            404, "UserNotFoundException", ex.getMessage(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(
            InvalidPasswordException ex, HttpServletRequest request) {
        return ResponseEntity.status(400).body(new ErrorResponse(
            400, "InvalidPasswordException", ex.getMessage(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(
            AuthenticationFailedException ex, HttpServletRequest request) {
        return ResponseEntity.status(401).body(new ErrorResponse(
            401, "AuthenticationFailedException", ex.getMessage(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetToken(
            InvalidResetTokenException ex, HttpServletRequest request) {
        return ResponseEntity.status(400).body(new ErrorResponse(
            400, "InvalidResetTokenException", ex.getMessage(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(
            UnauthorizedAccessException ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(new ErrorResponse(
            403, "UnauthorizedAccessException", ex.getMessage(), LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(400).body(new ErrorResponse(
            400, "ValidationException", message, LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(new ErrorResponse(
            403, "AccessDeniedException", "You do not have permission to access this resource",
            LocalDateTime.now(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(500).body(new ErrorResponse(
            500, "InternalServerError", "An unexpected error occurred",
            LocalDateTime.now(), request.getRequestURI()));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/microservice/people/exception/
git commit -m "feat(m002): add exception classes and GlobalExceptionHandler"
```

---

## Task 7: Security Layer (3 files)

**Files:**
- Create: `src/main/java/com/microservice/people/security/JwtTokenProvider.java`
- Create: `src/main/java/com/microservice/people/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/microservice/people/config/SecurityConfig.java`

- [ ] **Step 1: Create JwtTokenProvider.java**

```java
package com.microservice.people.security;

import com.microservice.people.entity.UserCredentials;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(UserCredentials user) {
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

    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserId(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                .getPayload().getSubject();
    }

    public String getEmail(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return (String) Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                .getPayload().get("email");
    }

    public List<String> getRoles(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String rolesStr = (String) Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
                .getPayload().get("roles");
        return Arrays.asList(rolesStr.split(","));
    }
}
```

- [ ] **Step 2: Create JwtAuthenticationFilter.java**

```java
package com.microservice.people.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final String cookieName = "AUTH_TOKEN";

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractTokenFromCookie(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserId(token);
            List<SimpleGrantedAuthority> authorities = jwtTokenProvider.getRoles(token).stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, authorities));
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 3: Create SecurityConfig.java**

```java
package com.microservice.people.config;

import com.microservice.people.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/microservice/people/security/ src/main/java/com/microservice/people/config/SecurityConfig.java
git commit -m "feat(m002): add JWT security layer (provider, filter, config)"
```

---

## Task 8: AuditService

**Files:**
- Create: `src/main/java/com/microservice/people/service/AuditService.java`

- [ ] **Step 1: Create AuditService.java**

```java
package com.microservice.people.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.people.entity.AuditLog;
import com.microservice.people.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.logging.Logger;

@Service
public class AuditService {

    private static final Logger logger = Logger.getLogger(AuditService.class.getName());
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String userId, String action, String tableName, String recordId,
                    Object oldValues, Object newValues, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setTableName(tableName);
            auditLog.setRecordId(recordId);
            auditLog.setOldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null);
            auditLog.setNewValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            logger.warning("Failed to log audit: " + e.getMessage());
        }
    }
}
```

---

## Task 9: AuthService

**Files:**
- Create: `src/main/java/com/microservice/people/service/AuthService.java`

- [ ] **Step 1: Create AuthService.java**

```java
package com.microservice.people.service;

import com.microservice.people.dto.LoginRequest;
import com.microservice.people.dto.SignupRequest;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserSession;
import com.microservice.people.exception.AuthenticationFailedException;
import com.microservice.people.exception.UserAlreadyExistsException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserSessionRepository;
import com.microservice.people.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private final UserCredentialsRepository userCredentialsRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordService passwordService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserCredentialsRepository userCredentialsRepository,
                       UserSessionRepository userSessionRepository,
                       PasswordService passwordService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordService = passwordService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse signup(SignupRequest req, String ip, String ua) {
        if (userCredentialsRepository.existsByEmail(req.getEmail())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }
        if (userCredentialsRepository.existsByPhone(req.getPhone())) {
            throw new UserAlreadyExistsException("User with this phone already exists");
        }
        passwordService.validate(req.getPassword());

        UserCredentials user = new UserCredentials();
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRoles("USER");
        user.setStatus(UserCredentials.Status.ACTIVE);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        userCredentialsRepository.save(user);

        UserResponse response = mapToUserResponse(user);
        auditService.log(user.getId(), "CREATE", "user_credentials", user.getId(), null, response, ip, ua);
        return response;
    }

    @Transactional
    public String login(LoginRequest req, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByEmailAndStatusNot(req.getEmail(), UserCredentials.Status.DELETED)
                .orElse(null);

        if (user == null) {
            auditService.log(null, "LOGIN_FAILED", "user_credentials", null, null, null, ip, ua);
            throw new AuthenticationFailedException("Invalid email or password");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            auditService.log(user.getId(), "LOGIN_FAILED", "user_credentials", user.getId(), null, null, ip, ua);
            throw new AuthenticationFailedException("Account locked. Try again later");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            user.setLoginAttemptCount(user.getLoginAttemptCount() + 1);
            if (user.getLoginAttemptCount() >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            userCredentialsRepository.save(user);
            auditService.log(user.getId(), "LOGIN_FAILED", "user_credentials", user.getId(), null, null, ip, ua);
            throw new AuthenticationFailedException("Invalid email or password");
        }

        user.setLoginAttemptCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        userCredentialsRepository.save(user);

        String jwt = jwtTokenProvider.generateToken(user);
        String tokenHash = sha256(jwt);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setTokenHash(tokenHash);
        session.setIpAddress(ip);
        session.setUserAgent(ua);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        userSessionRepository.save(session);

        auditService.log(user.getId(), "LOGIN", "user_sessions", session.getId(), null, null, ip, ua);
        return jwt;
    }

    private UserResponse mapToUserResponse(UserCredentials user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getPhone(), user.getRoles(),
                user.getStatus().toString(), user.getEmailVerified(), user.getPhoneVerified(),
                user.getEmailVerifiedAt(), user.getPhoneVerifiedAt(), user.getCreatedAt());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}
```

---

## Task 10: PasswordService

**Files:**
- Create: `src/main/java/com/microservice/people/service/PasswordService.java`

- [ ] **Step 1: Create PasswordService.java**

```java
package com.microservice.people.service;

import com.microservice.people.entity.PasswordResetToken;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.exception.AuthenticationFailedException;
import com.microservice.people.exception.InvalidPasswordException;
import com.microservice.people.exception.InvalidResetTokenException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.PasswordResetTokenRepository;
import com.microservice.people.repository.UserCredentialsRepository;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Service
public class PasswordService {

    private static final Logger logger = Logger.getLogger(PasswordService.class.getName());
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$");

    private final UserCredentialsRepository userCredentialsRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CacheManager cacheManager;

    public PasswordService(UserCredentialsRepository userCredentialsRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService,
                           CacheManager cacheManager) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.cacheManager = cacheManager;
    }

    public void validate(String password) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException(
                    "Password must be 8+ chars with 1 uppercase, 1 lowercase, 1 digit, 1 special char (!@#$%^&*)");
        }
    }

    @Transactional
    public void requestReset(String email, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByEmailAndStatusNot(email, UserCredentials.Status.DELETED)
                .orElse(null);
        if (user == null) return;  // no user enumeration

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        String tokenHash = sha256(token);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setEmailId(user.getId());
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        passwordResetTokenRepository.save(resetToken);

        logger.info("Password reset token for " + email + ": " + token);
        auditService.log(user.getId(), "PASSWORD_RESET_REQUEST", "password_reset_tokens",
                resetToken.getId(), null, null, ip, ua);
    }

    @Transactional
    public void performReset(String token, String newPassword, String ip, String ua) {
        String tokenHash = sha256(token);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidResetTokenException("Reset token is invalid, expired, or already used"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException("Reset token is invalid, expired, or already used");
        }
        if (resetToken.getUsedAt() != null) {
            throw new InvalidResetTokenException("Reset token is invalid, expired, or already used");
        }

        validate(newPassword);

        UserCredentials user = userCredentialsRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialsRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        cacheManager.getCache("users").evict(user.getId());
        auditService.log(user.getId(), "PASSWORD_RESET", "user_credentials", user.getId(),
                null, "{\"passwordChanged\": true}", ip, ua);
    }

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(userId, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialsRepository.save(user);

        cacheManager.getCache("users").evict(userId);
        auditService.log(userId, "UPDATE", "user_credentials", userId,
                "{\"passwordChanged\": true}", "{\"passwordChanged\": true}", ip, ua);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
```

---

## Task 11: UserService + SessionService

**Files:**
- Create: `src/main/java/com/microservice/people/service/UserService.java`
- Create: `src/main/java/com/microservice/people/service/SessionService.java`

- [ ] **Step 1: Create UserService.java**

```java
package com.microservice.people.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.people.dto.UpdateUserRequest;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserPersonalDetails;
import com.microservice.people.exception.UserAlreadyExistsException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserPersonalDetailsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserCredentialsRepository userCredentialsRepository;
    private final UserPersonalDetailsRepository userPersonalDetailsRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UserService(UserCredentialsRepository userCredentialsRepository,
                       UserPersonalDetailsRepository userPersonalDetailsRepository,
                       AuditService auditService,
                       ObjectMapper objectMapper) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.userPersonalDetailsRepository = userPersonalDetailsRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(String id) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userCredentialsRepository
                .findByStatusNot(UserCredentials.Status.DELETED, pageable)
                .map(this::mapToUserResponse);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public UserResponse updateUser(String id, UpdateUserRequest req, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UserPersonalDetails personalDetails = userPersonalDetailsRepository.findByUserId(id)
                .orElseGet(() -> {
                    UserPersonalDetails d = new UserPersonalDetails();
                    d.setUserId(id);
                    return d;
                });

        UserPersonalDetails oldDetails = objectMapper.convertValue(personalDetails, UserPersonalDetails.class);

        if (req.getFirstName() != null) personalDetails.setFirstName(req.getFirstName());
        if (req.getLastName() != null) personalDetails.setLastName(req.getLastName());
        if (req.getSalutation() != null) personalDetails.setSalutation(req.getSalutation());
        if (req.getDateOfBirth() != null) personalDetails.setDateOfBirth(req.getDateOfBirth());
        if (req.getGender() != null) personalDetails.setGender(UserPersonalDetails.Gender.valueOf(req.getGender()));
        if (req.getIdentityType() != null) personalDetails.setIdentityType(UserPersonalDetails.IdentityType.valueOf(req.getIdentityType()));
        if (req.getIdentityNumber() != null) personalDetails.setIdentityNumber(req.getIdentityNumber());
        if (req.getCitizenship() != null) personalDetails.setCitizenship(UserPersonalDetails.Citizenship.valueOf(req.getCitizenship()));
        if (req.getNationality() != null) personalDetails.setNationality(req.getNationality());
        if (req.getAddress1() != null) personalDetails.setAddress1(req.getAddress1());
        if (req.getAddress2() != null) personalDetails.setAddress2(req.getAddress2());
        if (req.getAddress3() != null) personalDetails.setAddress3(req.getAddress3());
        if (req.getCity() != null) personalDetails.setCity(req.getCity());
        if (req.getState() != null) personalDetails.setState(UserPersonalDetails.State.valueOf(req.getState()));
        if (req.getPostcode() != null) personalDetails.setPostcode(req.getPostcode());

        userPersonalDetailsRepository.save(personalDetails);
        auditService.log(id, "UPDATE", "user_personal_details", personalDetails.getId(),
                objectMapper.convertValue(oldDetails, Object.class),
                objectMapper.convertValue(personalDetails, Object.class), ip, ua);

        return mapToUserResponse(user);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String id, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setStatus(UserCredentials.Status.DELETED);
        userCredentialsRepository.save(user);
        auditService.log(id, "DELETE", "user_credentials", id, null, null, ip, ua);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateEmail(String id, String newEmail, String ip, String ua) {
        if (userCredentialsRepository.existsByEmail(newEmail)) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setEmail(newEmail);
        userCredentialsRepository.save(user);
        auditService.log(id, "UPDATE", "user_credentials", id, null, null, ip, ua);
    }

    private UserResponse mapToUserResponse(UserCredentials user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getPhone(), user.getRoles(),
                user.getStatus().toString(), user.getEmailVerified(), user.getPhoneVerified(),
                user.getEmailVerifiedAt(), user.getPhoneVerifiedAt(), user.getCreatedAt());
    }
}
```

- [ ] **Step 2: Create SessionService.java**

```java
package com.microservice.people.service;

import com.microservice.people.dto.SessionResponse;
import com.microservice.people.entity.UserSession;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final AuditService auditService;

    public SessionService(UserSessionRepository userSessionRepository, AuditService auditService) {
        this.userSessionRepository = userSessionRepository;
        this.auditService = auditService;
    }

    public SessionResponse getSession(String id) {
        return mapToSessionResponse(userSessionRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Session not found")));
    }

    public List<SessionResponse> getUserSessions(String userId) {
        return userSessionRepository.findByUserId(userId).stream()
                .map(this::mapToSessionResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(String id, String ip, String ua) {
        UserSession session = userSessionRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Session not found"));
        userSessionRepository.delete(session);
        auditService.log(session.getUserId(), "LOGOUT", "user_sessions", id, null, null, ip, ua);
    }

    @Transactional
    public void deleteAllSessions(String userId, String ip, String ua) {
        userSessionRepository.deleteByUserId(userId);
        auditService.log(userId, "LOGOUT_ALL", "user_sessions", null, null, null, ip, ua);
    }

    private SessionResponse mapToSessionResponse(UserSession session) {
        return new SessionResponse(
                session.getId(), session.getUserId(), session.getIpAddress(), session.getUserAgent(),
                session.getExpiresAt(), session.getCreatedAt(), session.getUpdatedAt());
    }
}
```

- [ ] **Step 3: Commit all services**

```bash
git add src/main/java/com/microservice/people/service/
git commit -m "feat(m002): add AuditService, AuthService, PasswordService, UserService, SessionService"
```

---

## Task 12: Verify — Build + Flyway Migration

- [ ] **Step 1: Ensure MySQL + Redis running**

```bash
systemctl status mysql redis-server
```

Expected: both active.

- [ ] **Step 2: Run bootRun — watch Flyway apply 5 migrations**

```bash
./gradlew bootRun 2>&1 | head -60
```

Expected output includes:
```
Flyway Community Edition ... by Redgate
Current version of schema `microservice_people_db`: << Empty Schema >>
Migrating schema `microservice_people_db` to version "1 ...
...
Successfully applied 5 migrations
```

App starts on port 8081 with no errors.

- [ ] **Step 3: Verify table schema**

```bash
mysql -u root -ptest123 microservice_people_db -e "DESCRIBE user_credentials; DESCRIBE password_reset_tokens;"
```

Expected:
- `user_credentials` has `email_verified_at` and `phone_verified_at` columns
- `password_reset_tokens` has `email_id` column (no `email` column)

- [ ] **Step 4: Verify compilation**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

---

## Verification Summary

End-to-end smoke test after all tasks complete:

1. `./gradlew compileJava` → BUILD SUCCESSFUL
2. `./gradlew bootRun` → app starts on 8081, Flyway applies 5 migrations cleanly
3. MySQL `DESCRIBE user_credentials` shows `email_verified_at`, `phone_verified_at`
4. MySQL `DESCRIBE password_reset_tokens` shows `email_id` (no `email`)
5. No `UserCredentials` or `UserCredentialsRepository` references remain in source

Unit + integration tests are Phase 5 scope per `doc/PHASE_2.md` (test starters replaced in Task 5 Step 2, ready for Phase 5).
