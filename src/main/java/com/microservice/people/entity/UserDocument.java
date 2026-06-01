package com.microservice.people.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_documents", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_upload_status", columnList = "upload_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('NRIC', 'PASSPORT')")
    private DocType docType;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(nullable = false, unique = true, length = 255)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PENDING', 'VERIFIED', 'REJECTED')")
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum DocType {
        NRIC, PASSPORT
    }

    public enum UploadStatus {
        PENDING, VERIFIED, REJECTED
    }
}
