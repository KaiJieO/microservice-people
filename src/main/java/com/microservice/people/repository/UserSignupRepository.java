package com.microservice.people.repository;

import com.microservice.people.entity.UserSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserSignupRepository extends JpaRepository<UserSignup, String> {
    Optional<UserSignup> findByEmail(String email);
    Optional<UserSignup> findByPhone(String phone);
}
