# PHASE 2 (Additional): Documents + Verifications

**Timeline:** 1-2 days
**Owner:** You
**Status:** DESIGN — pseudo code only (no implementation yet)
**Depends on:** Phase 2 core complete (entities, services, security, GlobalExceptionHandler)

---

## What

Two KYC features layered onto Phase 2. No controllers (Phase 3).

1. **user_documents** — KYC document uploads (NRIC or passport). 1:N per user, but one row per doc_type.
2. **user_verifications** — KYC review status. 1:1 per user.

> **user_preferences SKIPPED** — deferred to a future phase.

Deliverables: 2 migrations, 2 entities, 2 repositories, 5 DTOs, 1 new exception + 3 handler mappings, 2 services.

---

## UUID Rule

Same as Phase 2 — every PK and FK is `VARCHAR(36)`, `@GeneratedValue(strategy = GenerationType.UUID)`. DTOs expose ids as `String`.

---

## Migrations

Highest applied = V5. Verification timestamps (`email_verified_at`, `phone_verified_at`) already live in `V1__Create_User_Credentials.sql` — **no separate migration**. New files:

```
V6__Create_User_Documents.sql
V7__Create_User_Verifications.sql
```

### V6__Create_User_Documents.sql
```sql
CREATE TABLE user_documents (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    doc_type ENUM('NRIC', 'PASSPORT') NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_hash VARCHAR(255) NOT NULL UNIQUE,
    upload_status ENUM('PENDING', 'VERIFIED', 'REJECTED') DEFAULT 'PENDING',
    rejection_reason VARCHAR(500) NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_doc_type (user_id, doc_type),
    INDEX idx_user_id (user_id),
    INDEX idx_upload_status (upload_status)
);
```

### V7__Create_User_Verifications.sql
```sql
CREATE TABLE user_verifications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    kyc_status ENUM('PENDING', 'IN_REVIEW', 'VERIFIED', 'REJECTED') DEFAULT 'PENDING',
    submitted_at DATETIME NULL,
    verified_at DATETIME NULL,
    reviewer_id VARCHAR(36) NULL,
    reviewer_notes TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_kyc_status (kyc_status)
);
```

---

## Storage & Hashing (documents)

| Concern | Phase 2 (now) | Future (cloud) |
|---|---|---|
| `file_path` | local disk path (e.g. `/var/app/uploads/<uuid>.pdf`) | object key (e.g. `docs/<uuid>.pdf` in S3/GCS) |
| Storage base | config `app.storage.base` (localhost) | swap config only — no schema change |
| `file_hash` | SHA-256 of file bytes, supplied as request metadata | same |

- **file_hash purpose:** (a) dedupe — `UNIQUE` rejects same bytes twice; (b) tamper check — re-hash later, compare.
- **No JPA annotation for hashing** — `file_hash` stored as plain `VARCHAR(255)`. Hash computed outside entity.
- Real multipart upload + disk/S3 write = **Phase 3 controller**. This phase = metadata persistence only.

---

## Code Structure

```
src/main/java/com/microservice/people/
├── dto/
│   ├── DocumentUploadRequest.java
│   ├── DocumentReviewRequest.java
│   ├── DocumentResponse.java
│   ├── VerificationReviewRequest.java
│   └── VerificationResponse.java
├── entity/
│   ├── UserDocument.java
│   └── UserVerification.java
├── exception/
│   └── DuplicateDocumentException.java       (+ 3 handler mappings in GlobalExceptionHandler)
├── repository/
│   ├── UserDocumentRepository.java
│   └── UserVerificationRepository.java
└── service/
    ├── DocumentService.java
    └── VerificationService.java
```

> No `VerificationSubmitRequest` DTO — submit takes no body (no levels). Service method takes `userId` only.

---

## Entities

Both: `@Data @NoArgsConstructor @AllArgsConstructor`, enums as inner `EnumType.STRING`. Mirror `UserPersonalDetails` style.

### UserDocument
```
@Entity @Table(name="user_documents", indexes={idx_user_id, idx_upload_status})
class UserDocument:
    @Id @GeneratedValue(UUID) @Column(length=36)        String id
    @Column(nullable=false, length=36)                  String userId        // FK, NOT unique (1:N)
    @Enumerated(STRING) @Column(ENUM('NRIC','PASSPORT')) DocType docType
    @Column(nullable=false, length=500)                 String filePath
    @Column(nullable=false, unique=true, length=255)    String fileHash
    @Enumerated(STRING) @Column(ENUM('PENDING','VERIFIED','REJECTED')) UploadStatus uploadStatus = PENDING
    @Column(length=500)                                 String rejectionReason
    @CreationTimestamp ... createdAt
    @UpdateTimestamp   ... updatedAt
    enum DocType { NRIC, PASSPORT }
    enum UploadStatus { PENDING, VERIFIED, REJECTED }
```

### UserVerification
```
@Entity @Table(name="user_verifications", indexes={idx_user_id, idx_kyc_status})
class UserVerification:
    @Id @GeneratedValue(UUID) @Column(length=36)        String id
    @Column(nullable=false, unique=true, length=36)     String userId        // UNIQUE = 1:1
    @Enumerated(STRING) @Column(ENUM('PENDING','IN_REVIEW','VERIFIED','REJECTED')) KycStatus kycStatus = PENDING
    @Column(name="submitted_at", columnDefinition="datetime") LocalDateTime submittedAt
    @Column(name="verified_at",  columnDefinition="datetime") LocalDateTime verifiedAt
    @Column(name="reviewer_id", length=36)              String reviewerId    // admin id, nullable, no FK constraint
    @Column(columnDefinition="TEXT")                    String reviewerNotes
    @CreationTimestamp ... createdAt
    @UpdateTimestamp   ... updatedAt
    enum KycStatus { PENDING, IN_REVIEW, VERIFIED, REJECTED }
```

---

## Repositories

```
interface UserDocumentRepository extends JpaRepository<UserDocument, String>:
    List<UserDocument> findByUserId(String userId)
    Optional<UserDocument> findByUserIdAndDocType(String userId, DocType docType)   // Optional: one doc per type per user
    boolean existsByFileHash(String fileHash)

interface UserVerificationRepository extends JpaRepository<UserVerification, String>:
    Optional<UserVerification> findByUserId(String userId)                          // Optional: 1:1, may be absent
    Page<UserVerification> findByKycStatus(KycStatus status, Pageable pageable)      // future admin listing (paginated)
```

**Why Optional vs List**
- `findByUserId` (documents) → `List` — user has many docs (NRIC + passport).
- `findByUserIdAndDocType` → `Optional` — UNIQUE(user_id, doc_type) → at most one row.
- `findByUserId` (verifications) → `Optional` — UNIQUE user_id (1:1); may be empty (never submitted).
- `findByKycStatus` → `Page` — admin review queue could be thousands; paginate (chunk + total count). Dormant until admin UI (Phase 3+).

---

## DTOs

All: `@Data @NoArgsConstructor @AllArgsConstructor`. Validation via `jakarta.validation.constraints.*`.

### DocumentUploadRequest
```
@NotBlank String docType                  // NRIC | PASSPORT — parsed to enum in service
@NotBlank @Size(max=500) String filePath
@NotBlank @Size(max=255) String fileHash
```

### DocumentReviewRequest (admin verify/reject)
```
@NotBlank String uploadStatus             // VERIFIED | REJECTED
@Size(max=500) String rejectionReason     // required when REJECTED (service-validated)
```

### DocumentResponse
```
String id, userId, docType, filePath, fileHash, uploadStatus, rejectionReason
LocalDateTime createdAt, updatedAt
```

### VerificationReviewRequest (admin decision)
```
@NotBlank String kycStatus                // IN_REVIEW | VERIFIED | REJECTED
@Size(max=5000) String reviewerNotes
```

### VerificationResponse
```
String id, userId, kycStatus
LocalDateTime submittedAt, verifiedAt
String reviewerId, reviewerNotes
LocalDateTime createdAt, updatedAt
```

---

## Exceptions

| Class | HTTP | Default Message | Note |
|---|---|---|---|
| `DuplicateDocumentException` (new) | 409 | "Document of this type or with this content already exists" | extends RuntimeException |
| `IllegalStateException` (JDK) | 409 | (per throw site) | bad KYC transition |
| `IllegalArgumentException` (JDK) | 400 | (per throw site) | bad enum value / missing reject reason |
| `UserNotFoundException` (reuse) | 404 | "User not found" | user/record absent |

### GlobalExceptionHandler — add 3 handlers
```
@ExceptionHandler(DuplicateDocumentException.class) -> 409
@ExceptionHandler(IllegalStateException.class)      -> 409
@ExceptionHandler(IllegalArgumentException.class)   -> 400
// each builds ErrorResponse(status, exClass, ex.message, now, request.requestURI)
```

---

## Services

Both `@Service`. Audit via existing `auditService.log(userId, action, tableName, recordId, oldValues, newValues, ip, ua)` (REQUIRES_NEW). Map entity→Response inline (no MapStruct), like `UserService`.

### DocumentService

```
uploadDocument(userId, DocumentUploadRequest req, ip, ua) -> DocumentResponse   @Transactional
    1. docType = parseEnum(req.docType, DocType)            // invalid -> IllegalArgumentException (400)
    2. verify user: userCredentialsRepository.findByIdAndStatusNot(userId, DELETED)
                    -> absent throw UserNotFoundException
    3. if existsByFileHash(req.fileHash) -> throw DuplicateDocumentException   // same bytes
    4. if findByUserIdAndDocType(userId, docType).isPresent() -> throw DuplicateDocumentException  // one per type
    5. doc = new UserDocument(userId, docType, req.filePath, req.fileHash, PENDING, null)
    6. save
    7. audit.log(userId, "CREATE", "user_documents", doc.id, null, response, ip, ua)
    8. return map(doc)

getUserDocuments(userId) -> List<DocumentResponse>
    findByUserId(userId) -> map each

getDocument(id) -> DocumentResponse
    findById(id) -> absent throw UserNotFoundException ; map

reviewDocument(id, DocumentReviewRequest req, ip, ua) -> DocumentResponse   @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    1. doc = findById(id) -> absent throw UserNotFoundException
    2. status = parseEnum(req.uploadStatus, UploadStatus)   // must be VERIFIED|REJECTED, else IllegalArgumentException
    3. if status==REJECTED and blank(req.rejectionReason) -> throw IllegalArgumentException
    4. old = snapshot{uploadStatus, rejectionReason}
    5. doc.uploadStatus = status
       doc.rejectionReason = (status==REJECTED ? req.rejectionReason : null)
    6. save
    7. audit.log(doc.userId, "UPDATE", "user_documents", id, old, new, ip, ua)
    8. return map(doc)

deleteDocument(id, ip, ua)   @Transactional
    1. doc = findById(id) -> absent throw UserNotFoundException
    2. old = snapshot
    3. hard delete (no status column) ; repository.delete(doc)
    4. audit.log(doc.userId, "DELETE", "user_documents", id, old, null, ip, ua)
```

> **Decision applied:** documents = hard delete. Audit row preserves trail.

### VerificationService

```
getVerification(userId) -> VerificationResponse
    findByUserId(userId) -> absent throw UserNotFoundException ; map

submitVerification(userId, ip, ua) -> VerificationResponse   @Transactional
    1. verify user exists (findByIdAndStatusNot)
    2. v = findByUserId(userId).orElseGet(-> new UserVerification(userId))   // create-if-absent (1:1)
    3. guard: if v.kycStatus == VERIFIED -> throw IllegalStateException("already verified")
    4. old = snapshot (null if new)
    5. v.kycStatus = IN_REVIEW ; v.submittedAt = now()
    6. save
    7. audit.log(userId, old==null?"CREATE":"UPDATE", "user_verifications", v.id, old, new, ip, ua)
    8. return map(v)

reviewVerification(userId, reviewerId, VerificationReviewRequest req, ip, ua) -> VerificationResponse  @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    1. v = findByUserId(userId) -> absent throw UserNotFoundException
    2. target = parseEnum(req.kycStatus, KycStatus)
    3. assertTransitionAllowed(v.kycStatus, target)   // else IllegalStateException (409)
    4. old = snapshot
    5. v.kycStatus = target ; v.reviewerId = reviewerId ; v.reviewerNotes = req.reviewerNotes
       if target == VERIFIED -> v.verifiedAt = now()
    6. save
    7. audit.log(userId, "UPDATE", "user_verifications", v.id, old, new, ip, ua)
    8. return map(v)

listByStatus(KycStatus status, Pageable pageable) -> Page<VerificationResponse>   @PreAuthorize("hasRole('ADMIN')")
    findByKycStatus(status, pageable) -> map     // future admin queue
```

**KYC state machine** (`assertTransitionAllowed`)
```
PENDING    -> IN_REVIEW
IN_REVIEW  -> VERIFIED | REJECTED
REJECTED   -> IN_REVIEW          // resubmit/re-review
VERIFIED   -> (terminal, no transitions)
any other jump -> throw IllegalStateException
```

> **Decision applied:** VERIFIED does NOT auto-flip `user_personal_details.identity_verified` — cross-table coupling deferred (keep services decoupled).

---

## Security Considerations

1. **Admin-only review** — `reviewDocument` / `reviewVerification` / `listByStatus` guarded `@PreAuthorize("hasRole('ADMIN')")`.
2. **Dedupe** — `file_hash UNIQUE` + service check blocks duplicate bytes.
3. **One doc per type** — `UNIQUE(user_id, doc_type)` + service check.
4. **No file bytes in DB** — only path + hash. Bytes on disk (now) / object store (future).
5. **KYC terminal state** — VERIFIED cannot be re-submitted or downgraded.
6. **Audit on every write** — CREATE/UPDATE/DELETE logged with old/new JSON.
7. **No raw file content logged** — only metadata in audit JSON.

---

## Code Review Checklist

- [ ] All ids UUID (`VARCHAR(36)`) — PK + FK
- [ ] `UserDocument`: `file_hash UNIQUE`, `UNIQUE(user_id, doc_type)`
- [ ] `UserVerification`: `user_id UNIQUE` (1:1), no `verification_level`
- [ ] DTOs: `@Data @NoArgsConstructor @AllArgsConstructor` + validation annotations
- [ ] `DuplicateDocumentException` mapped 409; `IllegalState` 409; `IllegalArgument` 400
- [ ] `uploadDocument` checks both `existsByFileHash` AND `findByUserIdAndDocType`
- [ ] `reviewDocument` requires `rejectionReason` when REJECTED; clears it otherwise
- [ ] `submitVerification` blocks resubmit when VERIFIED
- [ ] `reviewVerification` enforces state machine; sets `verifiedAt` on VERIFIED
- [ ] Admin-only methods `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Audit logged on every write (CREATE/UPDATE/DELETE)
- [ ] Documents hard-deleted (no status column); verification never deleted
- [ ] No controllers (Phase 3)

---

## Testing Checklist

### Unit Tests
- [ ] `uploadDocument` — duplicate file_hash → `DuplicateDocumentException`
- [ ] `uploadDocument` — second doc of same type → `DuplicateDocumentException`
- [ ] `uploadDocument` — bad docType string → `IllegalArgumentException`
- [ ] `reviewDocument` — REJECTED without reason → `IllegalArgumentException`
- [ ] `submitVerification` — when VERIFIED → `IllegalStateException`
- [ ] `reviewVerification` — illegal transition (e.g. PENDING→VERIFIED) → `IllegalStateException`
- [ ] `reviewVerification` — VERIFIED sets `verifiedAt`

### Integration Tests
- [ ] Upload NRIC + PASSPORT → two rows; third (dup type) rejected
- [ ] Full KYC flow: submit (IN_REVIEW) → review VERIFIED → `verifiedAt` set
- [ ] Reject flow: submit → review REJECTED → resubmit (REJECTED→IN_REVIEW) allowed
- [ ] `listByStatus(PENDING, page)` returns paginated `Page<VerificationResponse>`
- [ ] Cascade: delete user → documents + verification removed (FK ON DELETE CASCADE)

---

## Effort Estimate

| Task | Estimate |
|---|---|
| Migrations V6 + V7 | 20 min |
| Entities (2) | 30 min |
| Repositories (2) | 15 min |
| DTOs (5) | 30 min |
| Exception + 3 handlers | 20 min |
| DocumentService | 45 min |
| VerificationService | 45 min |
| Unit + integration tests | 90 min |
| **Total** | **~5 hours (1 working day)** |

---

## Decisions (Locked)

| # | Decision | Outcome |
|---|---|---|
| 1 | Document delete | Hard delete; audit preserves trail |
| 2 | VERIFIED → flip `identity_verified` | Deferred (no cross-table coupling) |
| 3 | KYC state machine | Enforced; illegal jump → 409 |
| 4 | Migration numbers | V6 documents, V7 verifications (timestamps already in V1) |
| 5 | `verification_level` | Removed |
| 6 | `PROOF_OF_ADDRESS` | Removed; doc_type = NRIC \| PASSPORT |
| 7 | One doc per type | `UNIQUE(user_id, doc_type)` |
| 8 | user_preferences | Skipped → future phase |

---

## Next Phase

Phase 3 controllers add: `DocumentController` (multipart upload + disk/S3 write), `VerificationController`, admin review endpoints, paginated admin listing.

---

## References

- **TABLES.md** — schema (tables #6, #7)
- **PHASE_2.md** — core service/security patterns, audit, hashing strategy
- **CLAUDE.md** — scope boundary

---

## Last Updated

2026-06-01 — Documents (NRIC/PASSPORT, 1-per-type, SHA-256 dedupe), Verifications (no levels, KYC state machine),
             preferences skipped, migrations V6/V7
