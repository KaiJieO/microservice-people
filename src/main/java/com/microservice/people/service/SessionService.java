package com.microservice.people.service;

import com.microservice.people.dto.SessionResponse;
import com.microservice.people.entity.UserSession;
import com.microservice.people.exception.UserNotFoundException;
import com.microservice.people.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private final UserSessionRepository userSessionRepository;
    private final AuditService auditService;

    public SessionService(UserSessionRepository userSessionRepository, AuditService auditService) {
        this.userSessionRepository = userSessionRepository;
        this.auditService = auditService;
    }

    public SessionResponse getSession(String id) {
        return mapToSessionResponse(userSessionRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Session not found")));
    }

    public List<SessionResponse> getUserSessions(String userId) {
        return userSessionRepository.findByUserId(userId).stream()
                .map(this::mapToSessionResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(String id, String ip, String ua) {
        UserSession session = userSessionRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Session not found"));
        userSessionRepository.delete(session);
        auditService.log(session.getUserId(), "LOGOUT", "user_sessions", id, null, null, ip, ua);
    }

    @Transactional
    public void deleteAllSessions(String userId, String ip, String ua) {
        userSessionRepository.deleteByUserId(userId);
        auditService.log(userId, "LOGOUT_ALL", "user_sessions", null, null, null, ip, ua);
    }

    private SessionResponse mapToSessionResponse(UserSession session) {
        return new SessionResponse(
                session.getId(), session.getUserId(), session.getIpAddress(), session.getUserAgent(),
                session.getExpiresAt(), session.getCreatedAt(), session.getUpdatedAt());
    }
}
