# Claude Code Instructions — People Microservice

## Scope Boundary

**CRITICAL:** Do NOT implement additional or unannounced features, functions, or enhancements unless explicitly given permission by the user.

- Only implement what's asked
- No scope creep, no "while I'm at it" additions
- No refactoring beyond the immediate task
- No optimizations not requested
- No documentation extras unless asked

**If unsure whether something's in scope:** Ask first.

---

## Project Context

- **Type:** SIDE project (portfolio, may become industry standard)
- **Stack:** Spring Boot 3.2, MySQL 8.0 (local), Redis (local), Java 17+
- **Design:** Mirrors qr-microservice; 5 phases (entity → auth → controllers → caching → testing)
- **Key:** Local development only (no Docker Phase 1)
- **DB:** Flyway migrations, soft deletes, audit logging, Malaysia-specific data

---

## Phase Execution Order

1. **Phase 1:** Entities + Migrations (3-4 days)
2. **Phase 2:** Auth + Services (4-5 days)
3. **Phase 3:** Controllers + API (3-4 days)
4. **Phase 4:** Caching + Audit (2-3 days)
5. **Phase 5:** Testing + Verification (3-4 days)

**Total:** ~15 days

---

## Local Setup (Non-Negotiable)

Before running anything:
```bash
# 1. MySQL running on localhost:3306
mysql -u root -p
# password: test123

# 2. Redis running on localhost:6379
redis-cli ping
# should respond: PONG

# 3. Build + run
./gradlew build
./gradlew bootRun
```

---

## Code Standards

- **Reusability:** DRY principle, interfaces for contracts
- **Security:** Input validation, BCrypt hashing, JWT tokens, @PreAuthorize RBAC
- **Testing:** Unit + integration + E2E per phase
- **No premature abstractions:** 3 similar lines = refactor; 1 line = leave it
- **Comments:** Only when WHY is non-obvious
- **Soft delete:** Status ENUM (not hard DELETE)
- **Audit logging:** All changes captured in audit_logs table
- **Caching:** Hybrid (1-hour TTL + manual evict on write)

---

## When to Ask Before Acting

- Changing architecture (tech stack, database design, API structure)
- Adding dependencies
- Modifying verified specs from PLAN.md
- Refactoring beyond immediate task scope
- Adding tests beyond what's requested
- Changing port numbers, service names, or config
- Database schema changes not in TABLES.md
- Integrations not in the spec (email, SMS, payment, etc.)

---

## Files of Authority

- `PLAN.md` — overall architecture + timeline
- `TABLES.md` — database schema
- `CLAUDE.md` — this file (project constraints)
- Code + git history — source of truth for what's implemented

---

## Last Updated

2026-05-30