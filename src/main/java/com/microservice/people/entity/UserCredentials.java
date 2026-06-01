package com.microservice.people.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_credentials", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_phone", columnList = "phone"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 255)
    private String roles = "USER";

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')")
    private Status status = Status.ACTIVE;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean emailVerified = false;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean phoneVerified = false;

    @Column(name = "email_verified_at", columnDefinition = "datetime")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "phone_verified_at", columnDefinition = "datetime")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "last_login_at", columnDefinition = "datetime")
    private LocalDateTime lastLoginAt;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer loginAttemptCount = 0;

    @Column(name = "locked_until", columnDefinition = "datetime")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE, INACTIVE, SUSPENDED, DELETED
    }
}
