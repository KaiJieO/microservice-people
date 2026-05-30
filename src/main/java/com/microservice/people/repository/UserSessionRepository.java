package com.microservice.people.repository;

import com.microservice.people.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    List<UserSession> findByUserId(String userId);
    void deleteByUserId(String userId);
}
