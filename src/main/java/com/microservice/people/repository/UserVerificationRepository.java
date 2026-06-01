package com.microservice.people.repository;

import com.microservice.people.entity.UserVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserVerificationRepository extends JpaRepository<UserVerification, String> {
    Optional<UserVerification> findByUserId(String userId);

    Page<UserVerification> findByKycStatus(UserVerification.KycStatus status, Pageable pageable);
}
