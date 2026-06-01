package com.microservice.people.service;

import com.microservice.people.dto.VerificationReviewRequest;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserVerification;
import com.microservice.people.entity.UserVerification.KycStatus;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserVerificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationServiceTest {

    @Mock UserVerificationRepository userVerificationRepository;
    @Mock UserCredentialsRepository userCredentialsRepository;
    @Mock AuditService auditService;

    private VerificationService service() {
        return new VerificationService(userVerificationRepository, userCredentialsRepository, auditService);
    }

    private void userExists() {
        UserCredentials u = new UserCredentials();
        u.setId("u1");
        when(userCredentialsRepository.findByIdAndStatusNot("u1", UserCredentials.Status.DELETED))
                .thenReturn(Optional.of(u));
    }

    @Test
    void submit_whenAlreadyVerified_throwsIllegalState() {
        userExists();
        UserVerification v = new UserVerification();
        v.setId("v1");
        v.setUserId("u1");
        v.setKycStatus(KycStatus.VERIFIED);
        when(userVerificationRepository.findByUserId("u1")).thenReturn(Optional.of(v));

        assertThrows(IllegalStateException.class,
                () -> service().submitVerification("u1", "ip", "ua"));
    }

    @Test
    void submit_firstTime_setsInReview() {
        userExists();
        when(userVerificationRepository.findByUserId("u1")).thenReturn(Optional.empty());
        when(userVerificationRepository.save(any(UserVerification.class))).thenAnswer(i -> {
            UserVerification v = i.getArgument(0);
            v.setId("v1");
            return v;
        });

        var resp = service().submitVerification("u1", "ip", "ua");
        assertEquals("IN_REVIEW", resp.getKycStatus());
        assertNotNull(resp.getSubmittedAt());
    }

    @Test
    void review_illegalTransition_pendingToVerified_throws() {
        UserVerification v = new UserVerification();
        v.setId("v1");
        v.setUserId("u1");
        v.setKycStatus(KycStatus.PENDING);
        when(userVerificationRepository.findByUserId("u1")).thenReturn(Optional.of(v));

        assertThrows(IllegalStateException.class,
                () -> service().reviewVerification("u1", "admin1",
                        new VerificationReviewRequest("VERIFIED", "notes"), "ip", "ua"));
    }

    @Test
    void review_inReviewToVerified_setsVerifiedAt() {
        UserVerification v = new UserVerification();
        v.setId("v1");
        v.setUserId("u1");
        v.setKycStatus(KycStatus.IN_REVIEW);
        when(userVerificationRepository.findByUserId("u1")).thenReturn(Optional.of(v));
        when(userVerificationRepository.save(any(UserVerification.class))).thenAnswer(i -> i.getArgument(0));

        var resp = service().reviewVerification("u1", "admin1",
                new VerificationReviewRequest("VERIFIED", "ok"), "ip", "ua");
        assertEquals("VERIFIED", resp.getKycStatus());
        assertEquals("admin1", resp.getReviewerId());
        assertNotNull(resp.getVerifiedAt());
    }
}
