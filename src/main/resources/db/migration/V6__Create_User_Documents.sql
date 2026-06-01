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