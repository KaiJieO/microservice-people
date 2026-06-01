# Microservice People — Database Schema

**Last Updated:** May 29, 2026  
**All IDs:** UUID (VARCHAR(36))  
**Database:** MySQL 8.0  
**Migrations:** Flyway (sequential, append-only)

---

## Naming Convention

**Table prefix rule:**
- `user_*` = per-user data (one user owns the rows; `user_id` FK with `ON DELETE CASCADE`): `user_credentials`, `user_profile`, `user_sessions`, `user_documents`, `user_verifications`
- **unprefixed** = system / cross-cutting tables: `audit_logs` (events across all tables/users, `user_id` nullable), `password_reset_tokens` (transient auth plumbing)

> The prefix is a signal: `user_` → per-user domain row that cascades on delete; no prefix → system/infra table. Consistency of meaning over consistency of spelling — do NOT rename `audit_logs` to `user_audit_logs`.

**Layer convention:**
`snake_case` table → `PascalCase` entity (`@Table(name=...)`) → `<Entity>Repository` → `<Concept>Service` → `<Concept>Controller` → `<Verb><Noun>Request` / `<Noun>Response` DTOs → inner `EnumType.STRING` enums.

---

## Table Relationships

```
user_credentials (root)
├── user_profile (1:1, user_id FK)
├── user_sessions (1:N, user_id FK)
├── password_reset_tokens (1:N, user_id FK)
├── user_documents (1:N, user_id FK)
├── user_verifications (1:1, user_id FK)
├── user_bank_accounts (1:N, user_id FK)
├── user_devices (1:N, user_id FK)
├── user_preferences (1:1, user_id FK)
└── audit_logs (N:1, user_id FK — tracks changes to all tables)
```

---

## Core Tables (Phase 1)

### 1. user_credentials
**Purpose:** User authentication & signup credentials  
**Access Frequency:** High (every login, profile fetch)

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

**Indexes Rationale:**
- `idx_email`: Login lookup (unique constraint also indexes)
- `idx_phone`: Phone verification, duplicate prevention
- `idx_status`: Filter active users, exclude deleted
- `idx_created_at`: User creation timeline, pagination

---

### 2. user_profile
**Purpose:** User profile information (Malaysia-specific)  
**Access Frequency:** Medium (profile fetch, KYC)

```sql
CREATE TABLE user_profile (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    salutation VARCHAR(50),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender ENUM('M', 'F'),
    identity_type ENUM('NRIC', 'PASSPORT'),
    identity_number VARCHAR(20) NOT NULL,
    citizenship ENUM('MALAYSIAN', 'FOREIGNER'),
    nationality VARCHAR(100),
    address1 VARCHAR(255),
    address2 VARCHAR(255),
    address3 VARCHAR(255),
    city VARCHAR(100),
    state ENUM('JOHOR', 'KEDAH', 'KELANTAN', 'MALACCA', 'NEGERI_SEMBILAN', 'PAHANG', 'PENANG', 'PERAK', 'PERLIS', 'SABAH', 'SARAWAK', 'SELANGOR', 'TERENGGANU'),
    postcode VARCHAR(10),
    identity_verified BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_identity_number (identity_number),
    INDEX idx_citizenship (citizenship)
);
```

**Indexes Rationale:**
- `idx_user_id`: Foreign key join (1:1 lookup)
- `idx_identity_number`: KYC identity lookup, duplicate prevention
- `idx_citizenship`: Filter by residency status

---

### 3. password_reset_tokens
**Purpose:** Temporary tokens for password reset flow  
**Access Frequency:** Low (password reset only, ~1% of users)  
**TTL:** 5 minutes

```sql
CREATE TABLE password_reset_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
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

**Indexes Rationale:**
- `idx_token_hash`: Token validation lookup (unique constraint also indexes)
- `idx_expires_at`: Cleanup expired tokens (CRON job)
- `idx_user_id`: User token history

---

### 4. user_sessions
**Purpose:** Active user sessions (JWT token tracking)  
**Access Frequency:** High (session validation on each request)

```sql
CREATE TABLE user_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);
```

**Indexes Rationale:**
- `idx_user_id`: Find all sessions for user (logout all devices)
- `idx_expires_at`: Cleanup expired sessions (CRON job)

---

### 5. audit_logs
**Purpose:** Complete audit trail of all changes to any table  
**Access Frequency:** Medium (compliance queries, debugging)  
**Retention:** Permanent (legal requirement)

```sql
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36),
    action VARCHAR(50) NOT NULL,
    table_name VARCHAR(50) NOT NULL,
    record_id VARCHAR(36),
    old_values JSON,
    new_values JSON,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_table_name (table_name),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
);
```

**Indexes Rationale:**
- `idx_user_id`: User activity timeline
- `idx_table_name`: Find changes to specific table
- `idx_action`: Filter by action type (CREATE, UPDATE, DELETE, LOGIN)
- `idx_created_at`: Time-range compliance queries

---

## Future Tables (Structure Ready)

### 6. user_documents
**Purpose:** KYC document uploads (NRIC or passport)  
**Access Frequency:** Low (KYC phase only)  
**Storage:** `file_path` = pointer, not bytes. Localhost now (local disk path); cloud later (S3/GCS object key — swap via config, no schema change).  
**file_hash:** SHA-256 of file bytes. Dedupe + tamper check. Hashed in service (no JPA annotation).  
**Rule:** one doc per type per user (UNIQUE user_id + doc_type).

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

---

### 7. user_verifications
**Purpose:** KYC verification status tracking  
**Access Frequency:** Low (KYC phase only)

```sql
CREATE TABLE user_verifications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    kyc_status ENUM('PENDING', 'IN_REVIEW', 'VERIFIED', 'REJECTED') DEFAULT 'PENDING',
    submitted_at DATETIME,
    verified_at DATETIME,
    reviewer_id VARCHAR(36),
    reviewer_notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_kyc_status (kyc_status)
);
```

---

### 8. user_bank_accounts
**Purpose:** Bank account details for payments (encrypted)  
**Access Frequency:** Low (payments only)  
**Security:** account_number encrypted at rest

```sql
CREATE TABLE user_bank_accounts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    account_holder VARCHAR(255),
    bank_code VARCHAR(10),
    account_number VARCHAR(20),
    account_number_encrypted VARCHAR(255),
    account_type ENUM('SAVINGS', 'CURRENT', 'INVESTMENT'),
    is_primary BOOLEAN DEFAULT FALSE,
    verified BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id)
);
```

---

### 9. user_devices
**Purpose:** Device tracking for security & login context  
**Access Frequency:** Medium (device management)

```sql
CREATE TABLE user_devices (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    device_name VARCHAR(255),
    device_type ENUM('MOBILE', 'TABLET', 'DESKTOP'),
    os VARCHAR(100),
    browser VARCHAR(100),
    ip_address VARCHAR(45),
    last_seen_at DATETIME,
    is_trusted BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_last_seen_at (last_seen_at)
);
```

---

### 10. user_preferences
**Purpose:** User settings (language, notifications, theme)  
**Access Frequency:** Low (profile settings only)

```sql
CREATE TABLE user_preferences (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    language VARCHAR(10) DEFAULT 'EN',
    timezone VARCHAR(50) DEFAULT 'Asia/Kuala_Lumpur',
    newsletter_opt_in BOOLEAN DEFAULT TRUE,
    notification_email BOOLEAN DEFAULT TRUE,
    notification_sms BOOLEAN DEFAULT TRUE,
    notification_push BOOLEAN DEFAULT TRUE,
    theme ENUM('LIGHT', 'DARK') DEFAULT 'LIGHT',
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_credentials(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id)
);
```

---

## Migration Files

### Phase 1 (Core — implement first)
```
V1__Create_User_Credentials.sql
V2__Create_User_Profile.sql
V3__Create_Password_Reset_Tokens.sql
V4__Create_User_Sessions.sql
V5__Create_Audit_Logs.sql
```

### Phase 2+ (As needed)
```
V6__Create_User_Documents.sql
V7__Create_User_Verifications.sql
V8__Create_User_Bank_Accounts.sql
V9__Create_User_Devices.sql
V10__Create_User_Preferences.sql
```

---

## Index Strategy

**High Cardinality (many unique values):**
- email, phone → unique indexes (prevent duplicates + fast lookup)

**Foreign Keys:**
- All user_id indexes (join operations)

**Filtering/Querying:**
- status → exclude deleted rows
- is_deleted → soft-delete queries
- kyc_status, upload_status → workflow filtering

**Pagination/Sorting:**
- created_at → timeline queries, cursor-based pagination

**Cleanup (CRON jobs):**
- expires_at → find expired tokens/sessions for deletion

---

## Soft Delete Pattern

```sql
-- Query: Get active users only
SELECT * FROM user_credentials WHERE status != 'DELETED';

-- Delete: Mark as deleted (not DROP)
UPDATE user_credentials SET status = 'DELETED', updated_at = NOW() WHERE id = ?;

-- Cascade: Audit log entry
INSERT INTO audit_logs (user_id, action, table_name, record_id, new_values) 
VALUES (?, 'DELETE', 'user_credentials', ?, JSON_OBJECT('status', 'DELETED'));

-- Recovery: Reactivate if needed
UPDATE user_credentials SET status = 'ACTIVE' WHERE id = ?;
```

---

## Constraints Summary

| Constraint | Table | Purpose |
|---|---|---|
| UNIQUE | user_credentials.email | Prevent duplicate emails |
| UNIQUE | user_credentials.phone | Prevent duplicate phones |
| UNIQUE | password_reset_tokens.token_hash | One token per reset |
| UNIQUE | user_sessions.token_hash | One session per token |
| UNIQUE | user_profile.user_id | One profile per user |
| UNIQUE | user_verifications.user_id | One verification per user |
| UNIQUE | user_preferences.user_id | One preference per user |
| FK | All *_id columns | Referential integrity |

---

## Notes

- **Malaysia Compliance:** States, citizenship enums match Malaysian administrative divisions
- **NRIC Format:** 12 digits (YYMMDDSSSSSC)
- **Passport:** Alphanumeric, variable length
- **Timezone Default:** Asia/Kuala_Lumpur (Malaysia)
- **Soft Delete:** Uses status ENUM instead of is_deleted flag (clearer intent)
- **Audit Log Retention:** Permanent (no cleanup, legal requirement)
- **Token Hashing:** `password_reset_tokens.token_hash` and `user_sessions.token_hash` use **SHA-256** (deterministic → lookupable; no 72-byte BCrypt truncation). BCrypt is for passwords only.
- **Bank Encryption:** `user_bank_accounts.account_number_encrypted` uses **AES** at rest (future phase).
