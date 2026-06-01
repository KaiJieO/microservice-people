package com.microservice.people.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String phone;
    private String roles;
    private String status;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private LocalDateTime emailVerifiedAt;
    private LocalDateTime phoneVerifiedAt;
    private LocalDateTime createdAt;
}
