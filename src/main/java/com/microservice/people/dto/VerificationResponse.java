package com.microservice.people.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {
    private String id;
    private String userId;
    private String kycStatus;
    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;
    private String reviewerId;
    private String reviewerNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
