package com.microservice.people.service;

import com.microservice.people.dto.DocumentReviewRequest;
import com.microservice.people.dto.DocumentUploadRequest;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserDocument;
import com.microservice.people.exception.DuplicateDocumentException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    @Mock UserDocumentRepository userDocumentRepository;
    @Mock UserCredentialsRepository userCredentialsRepository;
    @Mock AuditService auditService;

    private DocumentService service() {
        return new DocumentService(userDocumentRepository, userCredentialsRepository, auditService);
    }

    private DocumentUploadRequest req(String type) {
        return new DocumentUploadRequest(type, "docs/x.pdf", "hash123");
    }

    private void userExists() {
        UserCredentials u = new UserCredentials();
        u.setId("u1");
        when(userCredentialsRepository.findByIdAndStatusNot("u1", UserCredentials.Status.DELETED))
                .thenReturn(Optional.of(u));
    }

    @Test
    void upload_badDocType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service().uploadDocument("u1", req("DRIVERS_LICENSE"), "ip", "ua"));
    }

    @Test
    void upload_duplicateHash_throws() {
        userExists();
        when(userDocumentRepository.existsByFileHash("hash123")).thenReturn(true);
        assertThrows(DuplicateDocumentException.class,
                () -> service().uploadDocument("u1", req("NRIC"), "ip", "ua"));
    }

    @Test
    void upload_duplicateType_throws() {
        userExists();
        when(userDocumentRepository.existsByFileHash("hash123")).thenReturn(false);
        when(userDocumentRepository.findByUserIdAndDocType("u1", UserDocument.DocType.NRIC))
                .thenReturn(Optional.of(new UserDocument()));
        assertThrows(DuplicateDocumentException.class,
                () -> service().uploadDocument("u1", req("NRIC"), "ip", "ua"));
    }

    @Test
    void review_rejectedWithoutReason_throwsIllegalArgument() {
        UserDocument doc = new UserDocument();
        doc.setId("d1");
        doc.setUserId("u1");
        when(userDocumentRepository.findById("d1")).thenReturn(Optional.of(doc));
        DocumentReviewRequest req = new DocumentReviewRequest("REJECTED", "  ");
        assertThrows(IllegalArgumentException.class,
                () -> service().reviewDocument("d1", req, "ip", "ua"));
    }
}
