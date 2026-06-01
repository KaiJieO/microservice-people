package com.microservice.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationReviewRequest {
    @NotBlank
    private String kycStatus;

    @Size(max = 5000)
    private String reviewerNotes;
}
