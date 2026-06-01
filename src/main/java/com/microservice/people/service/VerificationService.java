package com.microservice.people.service;

import com.microservice.people.dto.VerificationResponse;
import com.microservice.people.dto.VerificationReviewRequest;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserVerification;
import com.microservice.people.entity.UserVerification.KycStatus;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserVerificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VerificationService {

    private final UserVerificationRepository userVerificationRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final AuditService auditService;

    public VerificationService(UserVerificationRepository userVerificationRepository,
                               UserCredentialsRepository userCredentialsRepository,
                               AuditService auditService) {
        this.userVerificationRepository = userVerificationRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.auditService = auditService;
    }

    public VerificationResponse getVerification(String userId) {
        UserVerification v = userVerificationRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("Verification not found"));
        return mapToResponse(v);
    }

    @Transactional
    public VerificationResponse submitVerification(String userId, String ip, String ua) {
        userCredentialsRepository.findByIdAndStatusNot(userId, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UserVerification v = userVerificationRepository.findByUserId(userId).orElse(null);
        boolean isNew = (v == null);
        if (isNew) {
            v = new UserVerification();
            v.setUserId(userId);
        }
        if (v.getKycStatus() == KycStatus.VERIFIED) {
            throw new IllegalStateException("Verification already VERIFIED");
        }

        VerificationResponse oldValues = isNew ? null : mapToResponse(v);
        v.setKycStatus(KycStatus.IN_REVIEW);
        v.setSubmittedAt(LocalDateTime.now());
        userVerificationRepository.save(v);

        VerificationResponse newValues = mapToResponse(v);
        auditService.log(userId, isNew ? "CREATE" : "UPDATE", "user_verifications",
                v.getId(), oldValues, newValues, ip, ua);
        return newValues;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public VerificationResponse reviewVerification(String userId, String reviewerId,
                                                   VerificationReviewRequest req, String ip, String ua) {
        UserVerification v = userVerificationRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("Verification not found"));

        KycStatus target = KycStatus.valueOf(req.getKycStatus());
        assertTransitionAllowed(v.getKycStatus(), target);

        VerificationResponse oldValues = mapToResponse(v);
        v.setKycStatus(target);
        v.setReviewerId(reviewerId);
        v.setReviewerNotes(req.getReviewerNotes());
        if (target == KycStatus.VERIFIED) {
            v.setVerifiedAt(LocalDateTime.now());
        }
        userVerificationRepository.save(v);

        VerificationResponse newValues = mapToResponse(v);
        auditService.log(userId, "UPDATE", "user_verifications", v.getId(), oldValues, newValues, ip, ua);
        return newValues;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<VerificationResponse> listByStatus(KycStatus status, Pageable pageable) {
        return userVerificationRepository.findByKycStatus(status, pageable)
                .map(this::mapToResponse);
    }

    private void assertTransitionAllowed(KycStatus current, KycStatus target) {
        boolean allowed = switch (current) {
            case PENDING -> target == KycStatus.IN_REVIEW;
            case IN_REVIEW -> target == KycStatus.VERIFIED || target == KycStatus.REJECTED;
            case REJECTED -> target == KycStatus.IN_REVIEW;
            case VERIFIED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Illegal KYC transition: " + current + " -> " + target);
        }
    }

    private VerificationResponse mapToResponse(UserVerification v) {
        return new VerificationResponse(
                v.getId(), v.getUserId(),
                v.getKycStatus() != null ? v.getKycStatus().toString() : null,
                v.getSubmittedAt(), v.getVerifiedAt(),
                v.getReviewerId(), v.getReviewerNotes(),
                v.getCreatedAt(), v.getUpdatedAt());
    }
}
