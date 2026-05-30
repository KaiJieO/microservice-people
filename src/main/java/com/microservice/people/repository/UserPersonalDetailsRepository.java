package com.microservice.people.repository;

import com.microservice.people.entity.UserPersonalDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserPersonalDetailsRepository extends JpaRepository<UserPersonalDetails, String> {
    Optional<UserPersonalDetails> findByUserId(String userId);
    Optional<UserPersonalDetails> findByIdentityNumber(String identityNumber);
}
