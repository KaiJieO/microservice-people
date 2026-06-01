package com.microservice.people.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    @Size(max = 50)
    private String salutation;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    private LocalDate dateOfBirth;
    private String gender;
    private String identityType;

    @Size(max = 20)
    private String identityNumber;

    private String citizenship;

    @Size(max = 100)
    private String nationality;

    @Size(max = 255)
    private String address1;

    @Size(max = 255)
    private String address2;

    @Size(max = 255)
    private String address3;

    @Size(max = 100)
    private String city;

    private String state;

    @Size(max = 10)
    private String postcode;
}
