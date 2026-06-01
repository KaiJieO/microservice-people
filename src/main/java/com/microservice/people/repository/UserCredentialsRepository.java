package com.microservice.people.repository;

import com.microservice.people.entity.UserCredentials;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserCredentialsRepository extends JpaRepository<UserCredentials, String> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Optional<UserCredentials> findByEmailAndStatusNot(String email, UserCredentials.Status status);
    Optional<UserCredentials> findByIdAndStatusNot(String id, UserCredentials.Status status);
    Page<UserCredentials> findByStatusNot(UserCredentials.Status status, Pageable pageable);
}
