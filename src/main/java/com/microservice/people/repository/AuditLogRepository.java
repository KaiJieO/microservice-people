package com.microservice.people.repository;

import com.microservice.people.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByUserId(String userId);
    List<AuditLog> findByTableName(String tableName);
    List<AuditLog> findByAction(String action);
}
