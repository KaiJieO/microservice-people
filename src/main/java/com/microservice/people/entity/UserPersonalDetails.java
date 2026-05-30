package com.microservice.people.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_personal_details", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_identity_number", columnList = "identity_number"),
    @Index(name = "idx_citizenship", columnList = "citizenship")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPersonalDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 36)
    private String userId;

    @Column(length = 50)
    private String salutation;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('M', 'F')")
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('NRIC', 'PASSPORT')")
    private IdentityType identityType;

    @Column(nullable = false, length = 20)
    private String identityNumber;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('MALAYSIAN', 'FOREIGNER')")
    private Citizenship citizenship;

    @Column(length = 100)
    private String nationality;

    @Column(length = 255)
    private String address1;

    @Column(length = 255)
    private String address2;

    @Column(length = 255)
    private String address3;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('JOHOR', 'KEDAH', 'KELANTAN', 'MALACCA', 'NEGERI_SEMBILAN', 'PAHANG', 'PENANG', 'PERAK', 'PERLIS', 'SABAH', 'SARAWAK', 'SELANGOR', 'TERENGGANU')")
    private State state;

    @Column(length = 10)
    private String postcode;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean identityVerified = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, columnDefinition = "datetime default CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum Gender {
        M, F
    }

    public enum IdentityType {
        NRIC, PASSPORT
    }

    public enum Citizenship {
        MALAYSIAN, FOREIGNER
    }

    public enum State {
        JOHOR, KEDAH, KELANTAN, MALACCA, NEGERI_SEMBILAN, PAHANG, PENANG, PERAK, PERLIS, SABAH, SARAWAK, SELANGOR, TERENGGANU
    }
}
