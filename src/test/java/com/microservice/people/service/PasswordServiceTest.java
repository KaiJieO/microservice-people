package com.microservice.people.service;

import com.microservice.people.entity.UserCredentials;
import com.microservice.people.exception.AuthenticationFailedException;
import com.microservice.people.exception.InvalidPasswordException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.PasswordResetTokenRepository;
import com.microservice.people.repository.UserCredentialsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordServiceTest {

    @Mock UserCredentialsRepository userCredentialsRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock org.springframework.cache.CacheManager cacheManager;

    private PasswordService service() {
        return new PasswordService(userCredentialsRepository, passwordResetTokenRepository,
                passwordEncoder, auditService, cacheManager);
    }

    @Test
    void validate_acceptsStrongPassword() {
        assertDoesNotThrow(() -> service().validate("MyP@ssw0rd"));
    }

    @Test
    void validate_rejectsTooShort() {
        assertThrows(InvalidPasswordException.class, () -> service().validate("Ab1@"));
    }

    @Test
    void validate_rejectsNoUppercase() {
        assertThrows(InvalidPasswordException.class, () -> service().validate("myp@ssw0rd"));
    }

    @Test
    void validate_rejectsNoDigit() {
        assertThrows(InvalidPasswordException.class, () -> service().validate("MyP@ssword"));
    }

    @Test
    void validate_rejectsNoSpecial() {
        assertThrows(InvalidPasswordException.class, () -> service().validate("MyPassw0rd"));
    }

    @Test
    void changePassword_wrongOldPassword_throws() {
        UserCredentials user = new UserCredentials();
        user.setId("u1");
        user.setPasswordHash("hashed");
        when(userCredentialsRepository.findByIdAndStatusNot("u1", UserCredentials.Status.DELETED))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class,
                () -> service().changePassword("u1", "wrong", "NewP@ss1", "ip", "ua"));
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userCredentialsRepository.findByIdAndStatusNot("missing", UserCredentials.Status.DELETED))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> service().changePassword("missing", "old", "NewP@ss1", "ip", "ua"));
    }
}
