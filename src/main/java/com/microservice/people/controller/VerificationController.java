package com.microservice.people.controller;

import com.microservice.people.dto.VerificationResponse;
import com.microservice.people.dto.VerificationReviewRequest;
import com.microservice.people.entity.UserVerification;
import com.microservice.people.security.SecurityUtils;
import com.microservice.people.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verifications")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/user/{userId}/submit")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<VerificationResponse> submit(@PathVariable String userId,
                                                       HttpServletRequest httpReq) {
        return ResponseEntity.ok(verificationService.submitVerification(userId,
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent")));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("#userId == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<VerificationResponse> getVerification(@PathVariable String userId) {
        return ResponseEntity.ok(verificationService.getVerification(userId));
    }

    @PutMapping("/user/{userId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationResponse> review(@PathVariable String userId,
                                                       @Valid @RequestBody VerificationReviewRequest req,
                                                       HttpServletRequest httpReq) {
        return ResponseEntity.ok(verificationService.reviewVerification(userId,
                SecurityUtils.currentUserId(), req,
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent")));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<VerificationResponse>> listByStatus(
            @RequestParam UserVerification.KycStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(verificationService.listByStatus(status, pageable));
    }
}
