package com.microservice.people.service;

import com.microservice.people.entity.PasswordResetToken;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.exception.AuthenticationFailedException;
import com.microservice.people.exception.InvalidPasswordException;
import com.microservice.people.exception.InvalidResetTokenException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.PasswordResetTokenRepository;
import com.microservice.people.repository.UserCredentialsRepository;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Service
public class PasswordService {

    private static final Logger logger = Logger.getLogger(PasswordService.class.getName());
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$");

    private final UserCredentialsRepository userCredentialsRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CacheManager cacheManager;

    public PasswordService(UserCredentialsRepository userCredentialsRepository,
                           PasswordResetTokenRepository passwordResetTokenRepository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService,
                           CacheManager cacheManager) {
        this.userCredentialsRepository = userCredentialsRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.cacheManager = cacheManager;
    }

    public void validate(String password) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException(
                    "Password must be 8+ chars with 1 uppercase, 1 lowercase, 1 digit, 1 special char (!@#$%^&*)");
        }
    }

    @Transactional
    public void requestReset(String email, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByEmailAndStatusNot(email, UserCredentials.Status.DELETED)
                .orElse(null);
        if (user == null) return;  // no user enumeration

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        String tokenHash = sha256(token);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setEmailId(user.getId());
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        passwordResetTokenRepository.save(resetToken);

        logger.info("Password reset token for " + email + ": " + token);
        auditService.log(user.getId(), "PASSWORD_RESET_REQUEST", "password_reset_tokens",
                resetToken.getId(), null, null, ip, ua);
    }

    @Transactional
    public void performReset(String token, String newPassword, String ip, String ua) {
        String tokenHash = sha256(token);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidResetTokenException("Reset token is invalid, expired, or already used"));

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException("Reset token is invalid, expired, or already used");
        }
        if (resetToken.getUsedAt() != null) {
            throw new InvalidResetTokenException("Reset token is invalid, expired, or already used");
        }

        validate(newPassword);

        UserCredentials user = userCredentialsRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialsRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        cacheManager.getCache("users").evict(user.getId());
        auditService.log(user.getId(), "PASSWORD_RESET", "user_credentials", user.getId(),
                null, "{\"passwordChanged\": true}", ip, ua);
    }

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword, String ip, String ua) {
        UserCredentials user = userCredentialsRepository
                .findByIdAndStatusNot(userId, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialsRepository.save(user);

        cacheManager.getCache("users").evict(userId);
        auditService.log(userId, "UPDATE", "user_credentials", userId,
                "{\"passwordChanged\": true}", "{\"passwordChanged\": true}", ip, ua);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
