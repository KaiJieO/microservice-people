package com.microservice.people.controller;

import com.microservice.people.dto.UpdateEmailRequest;
import com.microservice.people.dto.UpdateProfileRequest;
import com.microservice.people.dto.UserProfileResponse;
import com.microservice.people.dto.UserResponse;
import com.microservice.people.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("#id == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("#id == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("#id == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> updateProfile(@PathVariable String id,
                                                             @Valid @RequestBody UpdateProfileRequest req,
                                                             HttpServletRequest httpReq) {
        return ResponseEntity.ok(userService.updateProfile(id, req,
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent")));
    }

    @PutMapping("/{id}/email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateEmail(@PathVariable String id,
                                            @Valid @RequestBody UpdateEmailRequest req,
                                            HttpServletRequest httpReq) {
        userService.updateEmail(id, req.getEmail(),
                httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String id,
                                           HttpServletRequest httpReq) {
        userService.deleteUser(id, httpReq.getRemoteAddr(), httpReq.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }
}
