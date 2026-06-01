package com.microservice.people.repository;

import com.microservice.people.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByUserId(String userId);

    Optional<UserProfile> findByIdentityNumber(String identityNumber);
}
