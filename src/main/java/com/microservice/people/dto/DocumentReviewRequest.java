package com.microservice.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReviewRequest {
    @NotBlank
    private String uploadStatus;

    @Size(max = 500)
    private String rejectionReason;
}
