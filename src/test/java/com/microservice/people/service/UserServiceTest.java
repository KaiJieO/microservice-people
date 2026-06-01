package com.microservice.people.service;

import com.microservice.people.dto.UpdateProfileRequest;
import com.microservice.people.dto.UserProfileResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserProfile;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock UserCredentialsRepository userCredentialsRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock AuditService auditService;

    private UserService service;

    @BeforeEach
    void setup() {
        service = new UserService(userCredentialsRepository, userProfileRepository,
                auditService, JsonMapper.builder().build());
    }

    @Test
    void getUserById_notFound_throws() {
        when(userCredentialsRepository.findByIdAndStatusNot("missing", UserCredentials.Status.DELETED))
                .thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.getUserById("missing"));
    }

    @Test
    void getProfile_notFound_throws() {
        when(userProfileRepository.findByUserId("missing")).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.getProfile("missing"));
    }

    @Test
    void updateProfile_appliesNonNullFields() {
        UserCredentials user = new UserCredentials();
        user.setId("u1");
        when(userCredentialsRepository.findByIdAndStatusNot("u1", UserCredentials.Status.DELETED))
                .thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(i -> {
            UserProfile p = i.getArgument(0);
            p.setId("p1");
            return p;
        });

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFirstName("Alice");
        req.setLastName("Tan");
        req.setState("SELANGOR");

        UserProfileResponse resp = service.updateProfile("u1", req, "ip", "ua");

        assertEquals("Alice", resp.getFirstName());
        assertEquals("Tan", resp.getLastName());
        assertEquals("SELANGOR", resp.getState());
        assertEquals("u1", resp.getUserId());
    }
}
