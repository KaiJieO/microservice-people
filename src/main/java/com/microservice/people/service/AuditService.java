package com.microservice.people.service;

import tools.jackson.databind.ObjectMapper;
import com.microservice.people.entity.AuditLog;
import com.microservice.people.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.logging.Logger;

@Service
public class AuditService {

    private static final Logger logger = Logger.getLogger(AuditService.class.getName());
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String userId, String action, String tableName, String recordId,
                    Object oldValues, Object newValues, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setTableName(tableName);
            auditLog.setRecordId(recordId);
            auditLog.setOldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null);
            auditLog.setNewValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            logger.warning("Failed to log audit: " + e.getMessage());
        }
    }
}
