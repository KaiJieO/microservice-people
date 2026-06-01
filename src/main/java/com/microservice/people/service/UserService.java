package com.microservice.people.service;

import tools.jackson.databind.ObjectMapper;
import com.microservice.people.dto.UpdateProfileRequest;
import com.microservice.people.dto.UserProfileResponse;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserProfile;
import com.microservice.people.exception.UserAlreadyExistsException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserProfileRepository;
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
    private final UserProfileRepository userProfileRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UserService(UserCredentialsRepository userCredentialsRepository,
                       UserProfileRepository userProfileRepository,
                       AuditService auditService,
                       ObjectMapper objectMapper) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.userProfileRepository = userProfileRepository;
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

    public UserProfileResponse getProfile(String id) {
        UserProfile profile = userProfileRepository.findByUserId(id)
                .orElseThrow(() -> new UserNotFoundException("Profile not found"));
        return mapToProfileResponse(profile);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userCredentialsRepository
                .findByStatusNot(UserCredentials.Status.DELETED, pageable)
                .map(this::mapToUserResponse);
    }

    @CacheEvict(value = "users", key = "#id")
    @Transactional
    public UserProfileResponse updateProfile(String id, UpdateProfileRequest req, String ip, String ua) {
        userCredentialsRepository
                .findByIdAndStatusNot(id, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UserProfile profile = userProfileRepository.findByUserId(id)
                .orElseGet(() -> {
                    UserProfile p = new UserProfile();
                    p.setUserId(id);
                    return p;
                });

        UserProfile oldProfile = objectMapper.convertValue(profile, UserProfile.class);

        if (req.getFirstName() != null) profile.setFirstName(req.getFirstName());
        if (req.getLastName() != null) profile.setLastName(req.getLastName());
        if (req.getSalutation() != null) profile.setSalutation(req.getSalutation());
        if (req.getDateOfBirth() != null) profile.setDateOfBirth(req.getDateOfBirth());
        if (req.getGender() != null) profile.setGender(UserProfile.Gender.valueOf(req.getGender()));
        if (req.getIdentityType() != null) profile.setIdentityType(UserProfile.IdentityType.valueOf(req.getIdentityType()));
        if (req.getIdentityNumber() != null) profile.setIdentityNumber(req.getIdentityNumber());
        if (req.getCitizenship() != null) profile.setCitizenship(UserProfile.Citizenship.valueOf(req.getCitizenship()));
        if (req.getNationality() != null) profile.setNationality(req.getNationality());
        if (req.getAddress1() != null) profile.setAddress1(req.getAddress1());
        if (req.getAddress2() != null) profile.setAddress2(req.getAddress2());
        if (req.getAddress3() != null) profile.setAddress3(req.getAddress3());
        if (req.getCity() != null) profile.setCity(req.getCity());
        if (req.getState() != null) profile.setState(UserProfile.State.valueOf(req.getState()));
        if (req.getPostcode() != null) profile.setPostcode(req.getPostcode());

        userProfileRepository.save(profile);
        auditService.log(id, "UPDATE", "user_profile", profile.getId(),
                objectMapper.convertValue(oldProfile, Object.class),
                objectMapper.convertValue(profile, Object.class), ip, ua);

        return mapToProfileResponse(profile);
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

    private UserProfileResponse mapToProfileResponse(UserProfile p) {
        return new UserProfileResponse(
                p.getId(), p.getUserId(), p.getSalutation(), p.getFirstName(), p.getLastName(),
                p.getDateOfBirth(),
                p.getGender() != null ? p.getGender().toString() : null,
                p.getIdentityType() != null ? p.getIdentityType().toString() : null,
                p.getIdentityNumber(),
                p.getCitizenship() != null ? p.getCitizenship().toString() : null,
                p.getNationality(), p.getAddress1(), p.getAddress2(), p.getAddress3(),
                p.getCity(),
                p.getState() != null ? p.getState().toString() : null,
                p.getPostcode(), p.getIdentityVerified(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
