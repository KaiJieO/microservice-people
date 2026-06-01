package com.microservice.people.service;

import com.microservice.people.dto.DocumentResponse;
import com.microservice.people.dto.DocumentReviewRequest;
import com.microservice.people.dto.DocumentUploadRequest;
import com.microservice.people.entity.UserCredentials;
import com.microservice.people.entity.UserDocument;
import com.microservice.people.exception.DuplicateDocumentException;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserCredentialsRepository;
import com.microservice.people.repository.UserDocumentRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentService {

    private final UserDocumentRepository userDocumentRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final AuditService auditService;

    public DocumentService(UserDocumentRepository userDocumentRepository,
                           UserCredentialsRepository userCredentialsRepository,
                           AuditService auditService) {
        this.userDocumentRepository = userDocumentRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentResponse uploadDocument(String userId, DocumentUploadRequest req, String ip, String ua) {
        UserDocument.DocType docType = UserDocument.DocType.valueOf(req.getDocType());

        userCredentialsRepository.findByIdAndStatusNot(userId, UserCredentials.Status.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (userDocumentRepository.existsByFileHash(req.getFileHash())) {
            throw new DuplicateDocumentException("Document with this content already exists");
        }
        if (userDocumentRepository.findByUserIdAndDocType(userId, docType).isPresent()) {
            throw new DuplicateDocumentException("Document of this type already exists for this user");
        }

        UserDocument doc = new UserDocument();
        doc.setUserId(userId);
        doc.setDocType(docType);
        doc.setFilePath(req.getFilePath());
        doc.setFileHash(req.getFileHash());
        doc.setUploadStatus(UserDocument.UploadStatus.PENDING);
        userDocumentRepository.save(doc);

        DocumentResponse response = mapToResponse(doc);
        auditService.log(userId, "CREATE", "user_documents", doc.getId(), null, response, ip, ua);
        return response;
    }

    public List<DocumentResponse> getUserDocuments(String userId) {
        return userDocumentRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public DocumentResponse getDocument(String id) {
        UserDocument doc = userDocumentRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Document not found"));
        return mapToResponse(doc);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentResponse reviewDocument(String id, DocumentReviewRequest req, String ip, String ua) {
        UserDocument doc = userDocumentRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Document not found"));

        UserDocument.UploadStatus status = UserDocument.UploadStatus.valueOf(req.getUploadStatus());
        if (status != UserDocument.UploadStatus.VERIFIED && status != UserDocument.UploadStatus.REJECTED) {
            throw new IllegalArgumentException("Review status must be VERIFIED or REJECTED");
        }
        if (status == UserDocument.UploadStatus.REJECTED
                && (req.getRejectionReason() == null || req.getRejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required when rejecting a document");
        }

        DocumentResponse oldValues = mapToResponse(doc);
        doc.setUploadStatus(status);
        doc.setRejectionReason(status == UserDocument.UploadStatus.REJECTED ? req.getRejectionReason() : null);
        userDocumentRepository.save(doc);

        DocumentResponse newValues = mapToResponse(doc);
        auditService.log(doc.getUserId(), "UPDATE", "user_documents", id, oldValues, newValues, ip, ua);
        return newValues;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDocument(String id, String ip, String ua) {
        UserDocument doc = userDocumentRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Document not found"));
        DocumentResponse oldValues = mapToResponse(doc);
        userDocumentRepository.delete(doc);
        auditService.log(doc.getUserId(), "DELETE", "user_documents", id, oldValues, null, ip, ua);
    }

    private DocumentResponse mapToResponse(UserDocument doc) {
        return new DocumentResponse(
                doc.getId(), doc.getUserId(),
                doc.getDocType() != null ? doc.getDocType().toString() : null,
                doc.getFilePath(), doc.getFileHash(),
                doc.getUploadStatus() != null ? doc.getUploadStatus().toString() : null,
                doc.getRejectionReason(), doc.getCreatedAt(), doc.getUpdatedAt());
    }
}
