package com.microservice.people.service;

import com.microservice.people.dto.LoginRequest;
import com.microservice.people.dto.SignupRequest;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserSession;
import com.microservice.people.exception.AuthenticationFailedException;
import com.microservice.people.exception.UserAlreadyExistsException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserSessionRepository;
import com.microservice.people.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private final UserCredentialsRepository userCredentialsRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordService passwordService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserCredentialsRepository userCredentialsRepository,
                       UserSessionRepository userSessionRepository,
                       PasswordService passwordService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordService = passwordService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse signup(SignupRequest req, String ip, String ua) {
        if (userCredentialsRepository.existsByEmail(req.getEmail())) {
            throw new UserAlreadyExistsException("User with this email already exists");
        }
        if (userCredentialsRepository.existsByPhone(req.getPhone())) {
            throw new UserAlreadyExistsException("User with this phone already exists");
        }
        passwordService.validate(req.getPassword());

        UserCredentials user = new UserCredentials();
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRoles("USER");
        user.setStatus(UserCredentials.Status.ACTIVE);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        userCredentialsRepository.save(user);

        UserResponse response = mapToUserResponse(user);
        auditService.log(user.getId(), "CREATE", "user_credentials", user.getId(), null, response, ip, ua);
        return response;
    }

    @Transactional
    public String login(LoginRequest req, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByEmailAndStatusNot(req.getEmail(), UserCredentials.Status.DELETED)
                .orElse(null);

        if (user == null) {
            auditService.log(null, "LOGIN_FAILED", "user_credentials", null, null, null, ip, ua);
            throw new AuthenticationFailedException("Invalid email or password");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            auditService.log(user.getId(), "LOGIN_FAILED", "user_credentials", user.getId(), null, null, ip, ua);
            throw new AuthenticationFailedException("Account locked. Try again later");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            user.setLoginAttemptCount(user.getLoginAttemptCount() + 1);
            if (user.getLoginAttemptCount() >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            }
            userCredentialsRepository.save(user);
            auditService.log(user.getId(), "LOGIN_FAILED", "user_credentials", user.getId(), null, null, ip, ua);
            throw new AuthenticationFailedException("Invalid email or password");
        }

        user.setLoginAttemptCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        userCredentialsRepository.save(user);

        String jwt = jwtTokenProvider.generateToken(user);
        String tokenHash = sha256(jwt);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setTokenHash(tokenHash);
        session.setIpAddress(ip);
        session.setUserAgent(ua);
        session.setExpiresAt(LocalDateTime.now().plusHours(24));
        userSessionRepository.save(session);

        auditService.log(user.getId(), "LOGIN", "user_sessions", session.getId(), null, null, ip, ua);
        return jwt;
    }

    private UserResponse mapToUserResponse(UserCredentials user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getPhone(), user.getRoles(),
                user.getStatus().toString(), user.getEmailVerified(), user.getPhoneVerified(),
                user.getEmailVerifiedAt(), user.getPhoneVerifiedAt(), user.getCreatedAt());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}
