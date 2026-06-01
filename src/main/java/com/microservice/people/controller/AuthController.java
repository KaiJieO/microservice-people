package com.microservice.people.controller;

import com.microservice.people.dto.LoginRequest;
import com.microservice.people.dto.SignupRequest;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.jwt.cookie-name:AUTH_TOKEN}")
    private String cookieName;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest req,
                                               HttpServletRequest httpReq) {
        UserResponse response = authService.signup(req, httpReq.getRemoteAddr(),
                httpReq.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest req,
                                                      HttpServletRequest httpReq) {
        String jwt = authService.login(req, httpReq.getRemoteAddr(),
                httpReq.getHeader("User-Agent"));

        ResponseCookie cookie = ResponseCookie.from(cookieName, jwt)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Strict")
                .maxAge(expirationMs / 1000)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "Login successful"));
    }
}
