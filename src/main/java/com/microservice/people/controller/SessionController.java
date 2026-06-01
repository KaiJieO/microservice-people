package com.microservice.people.controller;

import com.microservice.people.dto.SessionResponse;
import com.microservice.people.security.SecurityUtils;
import com.microservice.people.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    @Value("${app.jwt.cookie-name:AUTH_TOKEN}")
    private String cookieName;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String id) {
        SessionResponse session = sessionService.getSession(id);
        SecurityUtils.assertSelfOrAdmin(session.getUserId());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/user/{id}")
    @PreAuthorize("#id == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<List<SessionResponse>> getUserSessions(@PathVariable String id) {
        return ResponseEntity.ok(sessionService.getUserSessions(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable String id, HttpServletRequest httpReq) {
        SessionResponse session = sessionService.getSession(id);
        SecurityUtils.assertSelfOrAdmin(session.getUserId());
        sessionService.deleteSession(id, httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .build();
    }

    @DeleteMapping("/user/{id}/all")
    @PreAuthorize("#id == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAllSessions(@PathVariable String id, HttpServletRequest httpReq) {
        sessionService.deleteAllSessions(id, httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();
    }
}
