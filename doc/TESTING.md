# Testing Reference

Quick reference for testing concepts + tools used in this project. Phase 5 implements these.

---

## Two axes: tool vs scope

**Tool** = how tests run. **Scope** = what/how much you test. Orthogonal — combine freely.

```
JUnit 5            ← engine: finds @Test, runs, reports pass/fail
  └ Spring Test    ← @SpringBootTest / @WebMvcTest: boots Spring context
      └ MockMvc    ← fake HTTP client to call controllers (no real port)
```

`@Test`, assertions, `./gradlew test` = JUnit. `@SpringBootTest` / MockMvc = Spring add-ons JUnit drives. No JUnit → nothing runs.

---

## Tool choice (this stack: Spring Boot 4, Spring MVC)

| Tool | What | Industry | Curve | Fit |
|---|---|---|---|---|
| **MockMvc** | Controllers via mock servlet, no Tomcat | Very high (Spring default) | Low-med | ✅ **chosen** |
| WebTestClient | Reactive client (WebFlux-native) | High | Med | ✗ not reactive |
| RestAssured | Hits real running server, BDD style | High (QA/E2E) | Med | later, for E2E |

**Decision:** MockMvc now (already have `spring-restdocs-mockmvc` + `spring-boot-starter-test`). RestAssured later for true E2E.

---

## Test categories (scope)

| Category | Scope | Deps | Speed | Catches |
|---|---|---|---|---|
| **Unit** | 1 class | mocked (Mockito) | ms | logic branches |
| **Slice** | 1 controller | mock service | fast | routing, validation, `@PreAuthorize` |
| **Smoke** | full app, happy path | real / test DB | sec | wiring/config bugs |
| **Integration** | multi-layer (DB, Redis, HTTP) | real | sec | cross-layer issues |
| **E2E** | full system, real flow | real | slow | end-to-end behaviour |

**Smoke = real testing** — just shallow + broad. "Manual smoke" (curl/PowerShell, like the Phase 3 run) is real but **not automated**; "smoke as JUnit" is automated.

---

## Annotations cheat-sheet

```java
// UNIT — no Spring, mocks only
@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {
    @Mock UserCredentialsRepository repo;
    @InjectMocks PasswordService service;
    @Test void weakPassword_throws() {
        assertThrows(InvalidPasswordException.class, () -> service.validate("123"));
    }
}

// SLICE — one controller, service mocked
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;   // Boot 3.4+ (was @MockBean)
    @Test void getOther_forbidden() throws Exception {
        mvc.perform(get("/api/users/other-id")).andExpect(status().isForbidden());
    }
}

// SMOKE / INTEGRATION — full context + mock HTTP
@SpringBootTest
@AutoConfigureMockMvc
class AppSmokeTest {
    @Autowired MockMvc mvc;
    @Test void signupThenLogin() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON).content(json))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(json))
           .andExpect(status().isOk());
    }
}
```

---

## Test pyramid (industry norm)

```
      /\      few   — E2E / smoke (slow, wired)
     /  \     some  — slice / integration
    /____\    many  — unit (fast, mocked)
```

Many cheap unit tests, fewer expensive wired tests.

---

## Planned layout (Phase 5)

```
src/test/java/com/microservice/people/
├── service/
│   ├── PasswordServiceTest.java     UNIT  — validate() rules, reset flow
│   ├── AuthServiceTest.java         UNIT  — dup email/phone, lockout after 5
│   ├── UserServiceTest.java         UNIT  — updateProfile, getProfile not-found
│   ├── DocumentServiceTest.java     UNIT  — dup hash, one-per-type, review states
│   └── VerificationServiceTest.java UNIT  — KYC state machine, resubmit guard
├── controller/
│   └── UserControllerTest.java      SLICE — ownership @PreAuthorize 403
└── smoke/
    └── AppSmokeTest.java            SMOKE — signup→login→profile→cache→logout
```

---

## Pre-Phase-5 cleanup (flagged in PHASE_2.md)

- Confirm `build.gradle.kts` test deps = real artifacts. `spring-boot-starter-test` ✓. Watch for fake starters (`*-jpa-test`, `*-webmvc-test`) — replace with `spring-boot-starter-test`.
- Test profile: `BCryptPasswordEncoder(4)` for speed; `spring.cache.type=none` or Testcontainers Redis if Redis absent in CI.
- `@MockBean` deprecated → use `@MockitoBean` (Boot 3.4+).

---

## Notes from Phase 3 manual smoke

Manual PowerShell smoke caught real wiring bugs (missing `ObjectMapper` bean, Redis cache 500) that unit tests would have **missed** — proof wired/smoke tests earn their place. See `PHASE_3_SMOKE_TEST_REPORT.md`.

---

## Last Updated
2026-06-02
