package com.microservice.people.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequest {
    @NotBlank
    private String docType;

    @NotBlank
    @Size(max = 500)
    private String filePath;

    @NotBlank
    @Size(max = 255)
    private String fileHash;
}
