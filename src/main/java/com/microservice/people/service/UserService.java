package com.microservice.people.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.people.dto.UpdateUserRequest;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserPersonalDetails;
import com.microservice.people.exception.UserAlreadyExistsException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserPersonalDetailsRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserCredentialsRepository userCredentialsRepository;
    private final UserPersonalDetailsRepository userPersonalDetailsRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UserService(UserCredentialsRepository userCredentialsRepository,
                       UserPersonalDetailsRepository userPersonalDetailsRepository,
                       AuditService auditService,
                       ObjectMapper objectMapper) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.userPersonalDetailsRepository = userPersonalDetailsRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(String id) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userCredentialsRepository
                .findByStatusNot(UserCredentials.Status.DELETED, pageable)
                .map(this::mapToUserResponse);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public UserResponse updateUser(String id, UpdateUserRequest req, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UserPersonalDetails personalDetails = userPersonalDetailsRepository.findByUserId(id)
                .orElseGet(() -> {
                    UserPersonalDetails d = new UserPersonalDetails();
                    d.setUserId(id);
                    return d;
                });

        UserPersonalDetails oldDetails = objectMapper.convertValue(personalDetails, UserPersonalDetails.class);

        if (req.getFirstName() != null) personalDetails.setFirstName(req.getFirstName());
        if (req.getLastName() != null) personalDetails.setLastName(req.getLastName());
        if (req.getSalutation() != null) personalDetails.setSalutation(req.getSalutation());
        if (req.getDateOfBirth() != null) personalDetails.setDateOfBirth(req.getDateOfBirth());
        if (req.getGender() != null) personalDetails.setGender(UserPersonalDetails.Gender.valueOf(req.getGender()));
        if (req.getIdentityType() != null) personalDetails.setIdentityType(UserPersonalDetails.IdentityType.valueOf(req.getIdentityType()));
        if (req.getIdentityNumber() != null) personalDetails.setIdentityNumber(req.getIdentityNumber());
        if (req.getCitizenship() != null) personalDetails.setCitizenship(UserPersonalDetails.Citizenship.valueOf(req.getCitizenship()));
        if (req.getNationality() != null) personalDetails.setNationality(req.getNationality());
        if (req.getAddress1() != null) personalDetails.setAddress1(req.getAddress1());
        if (req.getAddress2() != null) personalDetails.setAddress2(req.getAddress2());
        if (req.getAddress3() != null) personalDetails.setAddress3(req.getAddress3());
        if (req.getCity() != null) personalDetails.setCity(req.getCity());
        if (req.getState() != null) personalDetails.setState(UserPersonalDetails.State.valueOf(req.getState()));
        if (req.getPostcode() != null) personalDetails.setPostcode(req.getPostcode());

        userPersonalDetailsRepository.save(personalDetails);
        auditService.log(id, "UPDATE", "user_personal_details", personalDetails.getId(),
                objectMapper.convertValue(oldDetails, Object.class),
                objectMapper.convertValue(personalDetails, Object.class), ip, ua);

        return mapToUserResponse(user);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String id, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setStatus(UserCredentials.Status.DELETED);
        userCredentialsRepository.save(user);
        auditService.log(id, "DELETE", "user_credentials", id, null, null, ip, ua);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateEmail(String id, String newEmail, String ip, String ua) {
        if (userCredentialsRepository.existsByEmail(newEmail)) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setEmail(newEmail);
        userCredentialsRepository.save(user);
        auditService.log(id, "UPDATE", "user_credentials", id, null, null, ip, ua);
    }

    private UserResponse mapToUserResponse(UserCredentials user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getPhone(), user.getRoles(),
                user.getStatus().toString(), user.getEmailVerified(), user.getPhoneVerified(),
                user.getEmailVerifiedAt(), user.getPhoneVerifiedAt(), user.getCreatedAt());
    }
}
