package com.microservice.people.controller;

import com.microservice.people.dto.DocumentResponse;
import com.microservice.people.dto.DocumentReviewRequest;
import com.microservice.people.dto.DocumentUploadRequest;
import com.microservice.people.security.SecurityUtils;
import com.microservice.people.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/user/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> upload(@PathVariable String userId,
                                                   @Valid @RequestBody DocumentUploadRequest req,
                                                   HttpServletRequest httpReq) {
        DocumentResponse response = documentService.uploadDocument(userId, req,
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<List<DocumentResponse>> getUserDocuments(@PathVariable String userId) {
        return ResponseEntity.ok(documentService.getUserDocuments(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable String id) {
        DocumentResponse doc = documentService.getDocument(id);
        SecurityUtils.assertSelfOrAdmin(doc.getUserId());
        return ResponseEntity.ok(doc);
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> review(@PathVariable String id,
                                                   @Valid @RequestBody DocumentReviewRequest req,
                                                   HttpServletRequest httpReq) {
        return ResponseEntity.ok(documentService.reviewDocument(id, req,
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest httpReq) {
        documentService.deleteDocument(id, httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }
}
