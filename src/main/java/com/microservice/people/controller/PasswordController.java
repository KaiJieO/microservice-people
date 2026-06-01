package com.microservice.people.controller;

import com.microservice.people.dto.ChangePasswordRequest;
import com.microservice.people.dto.PasswordResetConfirmRequest;
import com.microservice.people.dto.PasswordResetRequest;
import com.microservice.people.service.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/passwords")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @PostMapping("/reset-request")
    public ResponseEntity<Map<String, String>> requestReset(@Valid @RequestBody PasswordResetRequest req,
                                                            HttpServletRequest httpReq) {
        passwordService.requestReset(req.getEmail(),
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link has been sent"));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> performReset(@Valid @RequestBody PasswordResetConfirmRequest req,
                                                            HttpServletRequest httpReq) {
        passwordService.performReset(req.getToken(), req.getNewPassword(),
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("#id == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> changePassword(@PathVariable String id,
                                                             @Valid @RequestBody ChangePasswordRequest req,
                                                             HttpServletRequest httpReq) {
        passwordService.changePassword(id, req.getOldPassword(), req.getNewPassword(),
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
