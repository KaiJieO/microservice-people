package com.microservice.people.repository;

import com.microservice.people.entity.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, String> {
    List<UserDocument> findByUserId(String userId);

    Optional<UserDocument> findByUserIdAndDocType(String userId, UserDocument.DocType docType);

    boolean existsByFileHash(String fileHash);
}
