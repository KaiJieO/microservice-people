# PHASE 3: Controllers + API

**Timeline:** 3-4 days
**Owner:** You
**Status:** DESIGN — planning only (no implementation yet)
**Depends on:** Phase 2 core + Phase 2 Additional complete (services, security, exceptions all built + compiling)

---

## What

Expose REST endpoints over the existing service layer. No new business logic — controllers are thin: validate input, resolve caller identity, call service, shape HTTP response (status + cookie).

6 controllers:
1. `AuthController` — signup, login
2. `UserController` — profile CRUD, admin user management
3. `PasswordController` — reset flow, change password
4. `SessionController` — session list/get/logout
5. `DocumentController` — KYC document upload/review (JSON metadata)
6. `VerificationController` — KYC submit/review

---

## Locked Decisions

| # | Decision | Outcome |
|---|---|---|
| 1 | Ownership on self endpoints | `@PreAuthorize("#id == authentication.name or hasRole('ADMIN')")` — closes horizontal escalation |
| 2 | Admin-only endpoints | `@PreAuthorize("hasRole('ADMIN')")` (no id check) |
| 3 | Document upload | **JSON metadata** now (client supplies filePath + fileHash); multipart in future cloud phase |
| 4 | Service-to-service API key (`X-Service-Key`) | **Deferred** to a later phase |
| 5 | JWT transport | HTTP-only cookie (set on login, cleared on logout) |
| 6 | Caller identity | `authentication.name` = JWT subject = user UUID (set by `JwtAuthenticationFilter`) |

---

## Security Model

**Three layers, all already wired:**
1. **Authentication** — `JwtAuthenticationFilter` reads cookie, validates, sets `SecurityContext`. Anonymous on public endpoints.
2. **Role (RBAC)** — `hasRole('ADMIN')` / `hasRole('USER')`. Filter prefixes roles with `ROLE_`.
3. **Ownership** — `#id == authentication.name` compares path id to caller UUID. Admin bypasses via `or hasRole('ADMIN')`.

`@PreAuthorize` runs at **method level** (Spring AOP proxy, before body). `@EnableMethodSecurity` already on `SecurityConfig`. Annotate **controller** methods.

### Ownership matrix
| Endpoint | Guard |
|---|---|
| signup, login, reset-request, reset | public (permitAll in SecurityConfig) |
| GET/PUT `/api/users/{id}` | `#id == authentication.name or hasRole('ADMIN')` |
| GET `/api/users` (list) | `hasRole('ADMIN')` |
| PUT `/api/users/{id}/email`, DELETE `/api/users/{id}` | `hasRole('ADMIN')` |
| PUT `/api/passwords/{id}` (change) | `#id == authentication.name or hasRole('ADMIN')` |
| GET `/api/sessions/user/{id}`, DELETE `/api/sessions/user/{id}/all` | `#id == authentication.name or hasRole('ADMIN')` |
| GET/DELETE `/api/sessions/{id}` (by **session** id) | see note ↓ |
| documents/verifications self (GET, upload, submit) | `#userId == authentication.name or hasRole('ADMIN')` |
| documents/verifications review + listByStatus | `hasRole('ADMIN')` (already on service) |

> **Session-id nuance:** `/api/sessions/{id}` `{id}` is the **session** UUID, not the user UUID — `#id == authentication.name` won't match. Enforce ownership in the controller/service: load session, compare `session.userId` to `authentication.name`, throw `UnauthorizedAccessException` (403) if mismatch and not admin. `/api/sessions/user/{id}` paths use the user UUID → SpEL works directly.

---

## Caller Identity Helper

All write endpoints need `userId`, `ip`, `ua` for audit. Resolve in controller:

```
currentUserId() -> SecurityContextHolder.getContext().getAuthentication().getName()
ip(HttpServletRequest req)  -> req.getRemoteAddr()
ua(HttpServletRequest req)  -> req.getHeader("User-Agent")
```

> Optional: small `@RequestMapping` base or a private helper per controller. No new shared util unless 3+ controllers duplicate (DRY rule).

---

## Cookie Strategy

**Login** (`AuthController.login`):
1. `authService.login(req, ip, ua)` → returns JWT string
2. Build `ResponseCookie`:
   - name = `app.jwt.cookie-name` (AUTH_TOKEN)
   - value = JWT
   - `httpOnly(true)`, `secure(false)` local / `true` prod, `path("/")`, `sameSite("Strict")`, `maxAge(expiration-ms / 1000)`
3. `ResponseEntity.ok().header(SET_COOKIE, cookie.toString()).body(...)`

**Logout** (`SessionController.deleteSession` / `deleteAllSessions`):
- delete session row(s) via service, AND clear cookie (same name, `maxAge(0)`).

---

## Endpoints → Service Mapping

### AuthController  `/api/auth`
| Method | Path | Guard | Service call | Response |
|---|---|---|---|---|
| POST | /signup | public | `authService.signup(SignupRequest, ip, ua)` | 201, `UserResponse` |
| POST | /login | public | `authService.login(LoginRequest, ip, ua)` → JWT | 200, set cookie, `{message}` or `UserResponse` |

### UserController  `/api/users`
| Method | Path | Guard | Service call |
|---|---|---|---|
| GET | /{id} | self-or-admin | `userService.getUserById(id)` |
| GET | / | admin | `userService.getAllUsers(pageable)` |
| PUT | /{id} | self-or-admin | `userService.updateProfile(id, UpdateProfileRequest, ip, ua)` → `UserProfileResponse` |
| GET | /{id}/profile | self-or-admin | `userService.getProfile(id)` → `UserProfileResponse` |
| PUT | /{id}/email | admin | `userService.updateEmail(id, newEmail, ip, ua)` |
| DELETE | /{id} | admin | `userService.deleteUser(id, ip, ua)` → 204 |

> Pagination: `@PageableDefault(size=20)`; cap size ≤ 100. Status/citizenship filters from PLAN.md — `getAllUsers` currently takes only `Pageable`; filters are a future enhancement (note, don't expand service now).

### PasswordController  `/api/passwords`
| Method | Path | Guard | Service call |
|---|---|---|---|
| POST | /reset-request | public | `passwordService.requestReset(email, ip, ua)` → always 200 (no enumeration) |
| POST | /reset | public | `passwordService.performReset(token, newPassword, ip, ua)` |
| PUT | /{id} | self-or-admin | `passwordService.changePassword(id, old, new, ip, ua)` |

### SessionController  `/api/sessions`
| Method | Path | Guard | Service call |
|---|---|---|---|
| GET | /{id} | owner-of-session (service check) | `sessionService.getSession(id)` |
| GET | /user/{id} | self-or-admin | `sessionService.getUserSessions(id)` |
| DELETE | /{id} | owner-of-session (service check) | `sessionService.deleteSession(id, ip, ua)` + clear cookie → 204 |
| DELETE | /user/{id}/all | self-or-admin | `sessionService.deleteAllSessions(id, ip, ua)` + clear cookie → 204 |

### DocumentController  `/api/documents`   (NEW — Phase 2 Additional)
| Method | Path | Guard | Service call |
|---|---|---|---|
| POST | /user/{userId} | self-or-admin | `documentService.uploadDocument(userId, DocumentUploadRequest, ip, ua)` → 201 |
| GET | /user/{userId} | self-or-admin | `documentService.getUserDocuments(userId)` |
| GET | /{id} | owner-of-doc (service check) or admin | `documentService.getDocument(id)` |
| PUT | /{id}/review | admin | `documentService.reviewDocument(id, DocumentReviewRequest, ip, ua)` |
| DELETE | /{id} | admin | `documentService.deleteDocument(id, ip, ua)` → 204 |

> Body = `DocumentUploadRequest` JSON (`docType`, `filePath`, `fileHash`). No multipart. Future cloud: swap POST body to `multipart/form-data`, controller computes hash + stores bytes, then calls same service.

### VerificationController  `/api/verifications`   (NEW — Phase 2 Additional)
| Method | Path | Guard | Service call |
|---|---|---|---|
| POST | /user/{userId}/submit | self-or-admin | `verificationService.submitVerification(userId, ip, ua)` |
| GET | /user/{userId} | self-or-admin | `verificationService.getVerification(userId)` |
| PUT | /user/{userId}/review | admin | `verificationService.reviewVerification(userId, reviewerId=currentUserId, VerificationReviewRequest, ip, ua)` |
| GET | / | admin | `verificationService.listByStatus(status, pageable)` — `?status=PENDING&page=0&size=20` |

---

## Code Structure

```
src/main/java/com/microservice/people/controller/
├── AuthController.java
├── UserController.java
├── PasswordController.java
├── SessionController.java
├── DocumentController.java
└── VerificationController.java
```

> No new services/repositories/entities. Possibly small request DTOs for inline bodies:
> - `UpdateEmailRequest { @Email String email }` (for PUT /{id}/email)
> - reuse existing: `SignupRequest`, `LoginRequest`, `UpdateProfileRequest`, `PasswordResetRequest`, `PasswordResetConfirmRequest`, `ChangePasswordRequest`, `DocumentUploadRequest`, `DocumentReviewRequest`, `VerificationReviewRequest`.

---

## Controller Conventions

- `@RestController @RequestMapping("/api/...")`
- Constructor injection (match service style — no `@Autowired` field)
- `@Valid @RequestBody` on request DTOs → validation errors caught by existing `GlobalExceptionHandler` (`MethodArgumentNotValidException` → 400)
- Path vars `@PathVariable`, query `@RequestParam` / `Pageable`
- `@PreAuthorize` per ownership matrix
- Return `ResponseEntity<T>` with explicit status (201 create, 204 delete, 200 read/update)
- Inject `HttpServletRequest` for ip/ua
- No try/catch — let exceptions bubble to `GlobalExceptionHandler`

---

## SecurityConfig — update needed

`permitAll` list currently: `/api/auth/**`, `/api/passwords/reset-request`, `/api/passwords/reset`. **No change needed** — all new endpoints require auth (default `anyRequest().authenticated()`). Confirm `/api/auth/**` covers signup + login.

---

## Security Considerations

1. **Ownership enforced** — self endpoints use `#id == authentication.name or hasRole('ADMIN')`; session-by-id + doc-by-id use service-level owner check.
2. **HTTP-only cookie** — JWT not readable by JS (XSS mitigation).
3. **No enumeration** — `reset-request` returns 200 regardless.
4. **No token in response body** — JWT only in Set-Cookie.
5. **Admin-only** — list users, delete, change email, review docs/KYC, KYC listing.
6. **Validation** — `@Valid` on all bodies; `GlobalExceptionHandler` maps failures to 400.
7. **Audit** — already in services; controllers pass real ip/ua.
8. **CSRF** — disabled (stateless JWT, per SecurityConfig). Acceptable for cookie+SameSite=Strict; revisit if cross-site needed.

---

## Code Review Checklist

- [ ] Each controller `@RestController` + constructor injection
- [ ] `@Valid @RequestBody` on every request DTO
- [ ] `@PreAuthorize` matches ownership matrix exactly
- [ ] Self endpoints use `#id == authentication.name or hasRole('ADMIN')` (not RBAC-only)
- [ ] Session-by-id + document-by-id enforce owner check in service/controller
- [ ] Login sets HTTP-only cookie; logout clears it
- [ ] No JWT in response body
- [ ] Correct status codes (201/204/200)
- [ ] ip/ua passed to all write service calls
- [ ] No business logic in controllers (thin layer)
- [ ] Pagination size capped ≤ 100
- [ ] DocumentController = JSON metadata (no multipart)

---

## Testing Checklist

### Integration (MockMvc / @SpringBootTest)
- [ ] Signup → 201; duplicate → 409; weak password → 400
- [ ] Login → 200 + Set-Cookie; wrong password → 401
- [ ] GET /api/users/{ownId} → 200; GET other user's id as USER → 403; as ADMIN → 200
- [ ] GET /api/users (USER) → 403; (ADMIN) → 200 paginated
- [ ] PUT /api/users/{ownId} → 200; PUT /{id}/email as USER → 403
- [ ] DELETE /api/users/{id} as ADMIN → 204; then GET → 404
- [ ] reset-request unknown email → 200 (no enumeration)
- [ ] reset valid → 200; expired/used token → 400
- [ ] PUT /api/passwords/{ownId} wrong old → 401
- [ ] Sessions: list own → 200; other's session → 403; logout → 204 + cookie cleared
- [ ] Documents: upload NRIC → 201; dup type → 409; review as USER → 403; as ADMIN → 200
- [ ] Verifications: submit → IN_REVIEW; review VERIFIED as ADMIN → verifiedAt set; illegal transition → 409; listByStatus as USER → 403

### Security
- [ ] No cookie → protected endpoint 401/403
- [ ] Tampered/expired JWT → rejected
- [ ] Horizontal escalation blocked (USER cannot read/modify another USER)

---

## Effort Estimate

| Task | Estimate |
|---|---|
| AuthController + cookie logic | 60 min |
| UserController | 45 min |
| PasswordController | 30 min |
| SessionController + owner check | 45 min |
| DocumentController | 30 min |
| VerificationController | 30 min |
| UpdateEmailRequest DTO + SecurityConfig confirm | 20 min |
| Integration tests | 120 min |
| Manual verification (PLAN.md checklist 5–32) | 60 min |
| **Total** | **~7.5 hours (1.5 working days)** |

---

## Blockers & Mitigations

| Blocker | Mitigation |
|---|---|
| `authentication.name` not set to UUID | Verify `JwtAuthenticationFilter` sets principal = user id (subject) |
| Session/doc ownership not expressible in SpEL | Service-level owner check + `UnauthorizedAccessException` |
| `getAllUsers` filters (status/citizenship) in PLAN but not in service | Defer filters; expose `Pageable` only this phase (note, no scope creep) |
| Cookie `secure` flag local vs prod | `secure(false)` local, externalize for prod profile |
| CSRF disabled | Acceptable (stateless + SameSite=Strict); document for future review |

---

## Out of Scope (Phase 3)

- Multipart file upload + disk/S3 storage (future cloud phase)
- Service-to-service API key auth (`X-Service-Key`)
- Admin list filters (status/citizenship/sortBy) — `getAllUsers` stays `Pageable`-only
- Refresh-token endpoint
- Rate limiting, CORS hardening, email integration

---

## Next Phase

**Phase 4:** Caching + Audit verification (Redis hit/evict checks, audit completeness). **Phase 5:** Full test suite + verification checklist.

---

## References

- **PLAN.md** — REST endpoint table, auth levels, cookie/JWT spec, verification checklist
- **PHASE_2.md** — service method signatures, security layer
- **PHASE_2_ADDITIONAL.md** — document + verification services
- **CLAUDE.md** — scope boundary

---

## Last Updated

2026-06-01 — Controllers for auth/user/password/session + document/verification; ownership via
             `#id == authentication.name or hasRole('ADMIN')`; JSON-metadata doc upload; service auth deferred
