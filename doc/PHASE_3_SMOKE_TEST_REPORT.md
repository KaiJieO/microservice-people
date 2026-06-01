# Phase 3 — Smoke Test Report (Errors & Bottlenecks)

**Date:** 2026-06-02
**Context:** First real `bootRun` of the service (Phases 1–3 had only been compiled, never started). Triggered by the `user_personal_details` → `user_profile` rename verification. Surfaced several pre-existing runtime defects unrelated to the rename.

**Final result:** 14/14 smoke checks pass (signup, duplicate, weak-password, login, wrong-password, profile write/read, cache write/read, change-password, sessions, ownership 403, no-auth 403, Redis round-trip + evict).

---

## Error 1 — App failed to start: missing `ObjectMapper` bean

**Severity:** Critical (app would not boot)

**Symptom:**
```
APPLICATION FAILED TO START
Parameter 1 of constructor in com.microservice.people.service.AuditService
required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

**Root cause:**
`build.gradle.kts` uses **Spring Boot 4.0.6**, which ships **Jackson 3** (`tools.jackson.*`) as the default JSON stack and autoconfigures a `tools.jackson.databind.ObjectMapper` bean. But `AuditService` and `UserService` imported the **Jackson 2** type `com.fasterxml.jackson.databind.ObjectMapper` (present on the classpath only transitively via `jjwt-jackson`). Boot 4 does **not** create a bean for the Jackson 2 `ObjectMapper`, so constructor injection failed.

It compiled fine (class on classpath) but had no bean at runtime — a compile-vs-runtime mismatch only exposed on first boot.

**Fix:** switch the two service imports to the Boot-4-autoconfigured Jackson 3 mapper:
```java
import tools.jackson.databind.ObjectMapper;   // was com.fasterxml.jackson.databind.ObjectMapper
```
Files: `service/AuditService.java`, `service/UserService.java`.

**Why this is the right fix (vs hand-rolling a Jackson 2 bean):** the autoconfigured Jackson 3 mapper already registers `java.time` support, needed because audit JSON serializes `LocalDateTime`. A bare hand-built Jackson 2 `ObjectMapper` would have thrown on `LocalDateTime` without extra module wiring.

**Prevention:** when on Boot 4+, use `tools.jackson.*`; treat `com.fasterxml.jackson.*` as legacy. Don't depend on transitive Jackson 2 from `jjwt-jackson`.

---

## Error 2 — HTTP 500 on cached `GET /api/users/{id}`

**Severity:** Major (every cached read failed)

**Symptom:** `GET /api/users/{id}` returned 500, while the uncached `GET /api/users/{id}/profile` returned 200. The 500 had no stack trace in logs because `GlobalExceptionHandler`'s catch-all maps `Exception → 500 "An unexpected error occurred"` without logging the cause. Diagnosis came from the cached-vs-uncached contrast.

**Root cause (two layers):**

1. **Initial config** `RedisCacheManager.create(factory)` uses the default **`JdkSerializationRedisSerializer`**, which requires cached values to implement `java.io.Serializable`. `UserResponse` (a Lombok `@Data` DTO) does not → serialization threw on cache write.

2. **After switching to JSON** (`GenericJacksonJsonRedisSerializer(objectMapper)` with a plain mapper) the **write succeeded but the read-back 500'd**. The stored JSON had **no `@class` type info**:
   ```json
   {"id":"...","email":"carol@test.com", ...}   // no @class
   ```
   Without embedded type info the serializer cannot reconstruct the concrete `UserResponse` on read → deserialization failed.

**Fix:** configure the generic serializer via its builder with **default typing** enabled, scoped by a `PolymorphicTypeValidator` (so only our package + JDK types deserialize — avoids deserialization-gadget risk), plus Spring cache null-value support and a 1-hour TTL (per PLAN.md):
```java
PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.microservice.people.")
        .allowIfSubType("java.")
        .build();
GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(ptv)
        .enableSpringCacheNullValueSupport()
        .build();
RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(1))
        .serializeValuesWith(SerializationPair.fromSerializer(serializer));
```
File: `config/CacheConfig.java`.

**Verified:** stored value now carries `"@class":"com.microservice.people.dto.UserResponse"`; first GET writes, second GET reads back 200, `changePassword` `@CacheEvict` removes the key.

**Boot 4 / Jackson 3 note:** Spring Data Redis 4.0.5 ships `GenericJacksonJsonRedisSerializer` (Jackson 3) alongside the legacy `GenericJackson2JsonRedisSerializer` (Jackson 2). The Jackson-3 one has **no no-arg constructor** — it requires an `ObjectMapper` or the builder. The builder is needed to turn on type info; the plain constructor does not embed `@class`.

**Prevention:** for any Redis-cached DTO, either embed type info (generic + default typing) or use a type-bound serializer; don't rely on the bare constructor.

---

## Bottleneck 3 — Flyway checksum change forced a DB recreate

**Severity:** Operational (manual step, local only)

**Symptom:** the `user_personal_details` → `user_profile` rename edited an already-applied migration (`V2`), changing its Flyway checksum. On an existing DB this would fail `validate`.

**Resolution:** local-dev path — drop + recreate the schema so Flyway re-applies V1–V7 fresh:
```sql
DROP DATABASE IF EXISTS microservice_people_db; CREATE DATABASE microservice_people_db;
```
Flyway then applied all 7 migrations cleanly (incl. `V2 - Create User Profile`).

**Prevention / production note:** never edit an applied migration in a shared/prod environment. There, rename via a **new** forward migration (`Vn__Rename_user_personal_details.sql` with `RENAME TABLE ...`). Editing-in-place is acceptable only because this is local-dev with disposable data.

---

## Minor — `mysql` CLI stderr noise in PowerShell

**Severity:** Cosmetic

**Symptom:** `mysql -p...` prints `[Warning] Using a password on the command line interface can be insecure.` to stderr; PowerShell surfaces it as a `NativeCommandError` and flips `$?` to false even on success.

**Resolution:** used `$env:MYSQL_PWD` for the verification call and relied on `$LASTEXITCODE` rather than `$?`. No functional impact.

---

## Summary of changes made during smoke testing

| File | Change | Reason |
|---|---|---|
| `service/AuditService.java` | Jackson 2 → Jackson 3 `ObjectMapper` import | Error 1 |
| `service/UserService.java` | Jackson 2 → Jackson 3 `ObjectMapper` import | Error 1 |
| `config/CacheConfig.java` | JSON serializer + default typing + 1h TTL | Error 2 |

All three are **pre-existing defects** unrelated to the rename — they were dormant because the app had never been run before, only compiled.

---

## Outstanding (not blocking)

- `GlobalExceptionHandler` swallows the catch-all `Exception` without logging the stack trace, which made Error 2 harder to diagnose. Consider `logger.error(...)` in the 500 handler.
- `spring.jpa.open-in-view` enabled by default (Boot warning). Consider disabling explicitly.
- Admin-only and document/verification endpoints were not exercised in this smoke run (no ADMIN user seeded). Cover in Phase 5 integration tests.
