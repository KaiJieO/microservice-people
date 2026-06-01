package com.microservice.people.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private String id;
    private String userId;
    private String salutation;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String identityType;
    private String identityNumber;
    private String citizenship;
    private String nationality;
    private String address1;
    private String address2;
    private String address3;
    private String city;
    private String state;
    private String postcode;
    private Boolean identityVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
