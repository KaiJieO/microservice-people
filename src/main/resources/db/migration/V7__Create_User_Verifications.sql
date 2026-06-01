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
