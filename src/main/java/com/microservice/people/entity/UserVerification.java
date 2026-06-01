package com.microservice.people.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_verifications", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_kyc_status", columnList = "kyc_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('PENDING', 'IN_REVIEW', 'VERIFIED', 'REJECTED')")
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "submitted_at", columnDefinition = "datetime")
    private LocalDateTime submittedAt;

    @Column(name = "verified_at", columnDefinition = "datetime")
    private LocalDateTime verifiedAt;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Column(columnDefinition = "TEXT")
    private String reviewerNotes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum KycStatus {
        PENDING, IN_REVIEW, VERIFIED, REJECTED
    }
}
