package com.microservice.people.service;

import com.microservice.people.dto.LoginRequest;
import com.microservice.people.dto.SignupRequest;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.exception.AuthenticationFailedException;
import com.microservice.people.exception.UserAlreadyExistsException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserSessionRepository;
import com.microservice.people.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserCredentialsRepository userCredentialsRepository;
    @Mock UserSessionRepository userSessionRepository;
    @Mock PasswordService passwordService;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    private AuthService service() {
        return new AuthService(userCredentialsRepository, userSessionRepository, passwordService,
                jwtTokenProvider, passwordEncoder, auditService);
    }

    private SignupRequest signupReq() {
        return new SignupRequest("a@b.com", "+60123456789", "MyP@ssw0rd");
    }

    @Test
    void signup_duplicateEmail_throws() {
        when(userCredentialsRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThrows(UserAlreadyExistsException.class,
                () -> service().signup(signupReq(), "ip", "ua"));
    }

    @Test
    void signup_duplicatePhone_throws() {
        when(userCredentialsRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(userCredentialsRepository.existsByPhone("+60123456789")).thenReturn(true);
        assertThrows(UserAlreadyExistsException.class,
                () -> service().signup(signupReq(), "ip", "ua"));
    }

    @Test
    void signup_success_returnsUserResponse() {
        when(userCredentialsRepository.existsByEmail(any())).thenReturn(false);
        when(userCredentialsRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode("MyP@ssw0rd")).thenReturn("hashed");
        when(userCredentialsRepository.save(any(UserCredentials.class))).thenAnswer(i -> {
            UserCredentials u = i.getArgument(0);
            u.setId("u1");
            return u;
        });

        UserResponse resp = service().signup(signupReq(), "ip", "ua");
        assertEquals("a@b.com", resp.getEmail());
        verify(passwordService).validate("MyP@ssw0rd");
        verify(auditService).log(any(), eq("CREATE"), eq("user_credentials"), any(), any(), any(), any(), any());
    }

    @Test
    void login_userNotFound_throwsAndAuditsFailure() {
        when(userCredentialsRepository.findByEmailAndStatusNot("a@b.com", UserCredentials.Status.DELETED))
                .thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class,
                () -> service().login(new LoginRequest("a@b.com", "x"), "ip", "ua"));
        verify(auditService).log(isNull(), eq("LOGIN_FAILED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void login_wrongPassword_incrementsAttempts() {
        UserCredentials user = new UserCredentials();
        user.setId("u1");
        user.setPasswordHash("hashed");
        user.setLoginAttemptCount(0);
        when(userCredentialsRepository.findByEmailAndStatusNot("a@b.com", UserCredentials.Status.DELETED))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("x", "hashed")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class,
                () -> service().login(new LoginRequest("a@b.com", "x"), "ip", "ua"));
        assertEquals(1, user.getLoginAttemptCount());
    }

    @Test
    void login_fifthWrongPassword_locksAccount() {
        UserCredentials user = new UserCredentials();
        user.setId("u1");
        user.setPasswordHash("hashed");
        user.setLoginAttemptCount(4);
        when(userCredentialsRepository.findByEmailAndStatusNot("a@b.com", UserCredentials.Status.DELETED))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("x", "hashed")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class,
                () -> service().login(new LoginRequest("a@b.com", "x"), "ip", "ua"));
        assertEquals(5, user.getLoginAttemptCount());
        assertNotNull(user.getLockedUntil());
    }
}
