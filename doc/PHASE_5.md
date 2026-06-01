# PHASE 5: Testing

**Status:** IN PROGRESS (5a done; 5b/5c pending)
**Depends on:** Phases 1–4 complete + app boots (smoke verified)

## What
Automated JUnit 5 tests in three tiers. Only **5a (unit)** is implemented. Slice + wired tests are designed but not yet written.

## Test tiers

| Tier | Annotation | Tool | Infra needed | Status |
|---|---|---|---|---|
| 5a Unit | `@ExtendWith(MockitoExtension.class)` | JUnit + Mockito | none | ✅ DONE |
| 5b Slice | `@WebMvcTest(XController.class)` | MockMvc + mocked service | none (CI-safe) | ⬜ TODO |
| 5c Smoke/Integration | `@SpringBootTest @AutoConfigureMockMvc` | MockMvc + full context | MySQL + Redis (or Testcontainers) | ⬜ TODO |

> **Current tests do NOT use `@SpringBootTest` or MockMvc.** They are pure Mockito unit tests — no Spring context, no HTTP, no DB/Redis. The only `@SpringBootTest` in the repo is the untouched `MicroservicePeopleApplicationTests.contextLoads` stub (not run; needs infra).

## 5a — Unit tests (DONE — 24 tests, 0 failures)
```
src/test/java/com/microservice/people/service/
├── PasswordServiceTest      (7) — validate() rules ×5, changePassword wrong-old, user-not-found
├── AuthServiceTest          (6) — signup dup email/phone, signup success, login absent/wrong-pw/lockout-after-5
├── UserServiceTest          (3) — getUserById/getProfile not-found, updateProfile applies fields
├── DocumentServiceTest      (4) — bad docType, dup hash, dup type, reject-without-reason
└── VerificationServiceTest  (4) — submit-when-verified, first-submit, illegal KYC transition, verified-at set
```
Run: `./gradlew test --tests "com.microservice.people.service.*"` → BUILD SUCCESSFUL.

## 5b — Controller slice tests (TODO, CI-safe)
`@WebMvcTest` per controller, service `@MockitoBean`. No DB/Redis. Covers what unit tests can't:
```
src/test/java/com/microservice/people/controller/
├── UserControllerTest        — ownership @PreAuthorize: self 200, other-user 403, admin bypass
├── AuthControllerTest        — signup 201, login sets cookie, validation 400
├── PasswordControllerTest    — public reset endpoints, change-password ownership
├── SessionControllerTest     — session owner check, logout clears cookie
├── DocumentControllerTest    — upload self-or-admin, review admin-only 403
└── VerificationControllerTest— submit self, review/list admin-only
```
Focus: routing, `@Valid` 400s, `@PreAuthorize` 401/403, cookie headers. Security via `spring-security-test` (`@WithMockUser`, `csrf()`).

## 5c — Smoke / integration tests (TODO, needs infra)
`@SpringBootTest @AutoConfigureMockMvc` — full wiring, real DB+Redis. Mirrors the Phase 3 manual smoke as code:
```
src/test/java/com/microservice/people/integration/
└── AppSmokeTest — signup → login → PUT profile → GET profile → cached GET → change-password → logout
```
Requires MySQL(3306)+Redis(6379) or **Testcontainers** for CI. Catches wiring/config bugs that mocked tests miss (e.g. the Boot-4 `ObjectMapper` bean + Redis cache 500 found in Phase 3 — see `PHASE_3_SMOKE_TEST_REPORT.md`).

## Out of scope
- JaCoCo coverage tooling
- Performance/load tests
- `contextLoads` stub left as-is (needs infra)

## Pre-work before 5b/5c
- Add `spring-security-test` to `testImplementation` (for `@WithMockUser`, `csrf()`)
- `@MockBean` deprecated → use `@MockitoBean` (Boot 3.4+)
- Test profile: `BCryptPasswordEncoder(4)`; `spring.cache.type=none` or Testcontainers Redis for CI

## Verify
- 5a now: `./gradlew test --tests "com.microservice.people.service.*"`
- 5b/5c later: `./gradlew test` (full suite, infra up)

## Last Updated
2026-06-02
