package com.gsr.onboarding.service;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsr.onboarding.config.CorrelationIdFilter;
import com.gsr.onboarding.domain.entity.AuditEvent;
import com.gsr.onboarding.repository.AuditEventRepository;

/** Writes the audit trail. Joins the caller's transaction so audit and change commit together. */
@Service
public class AuditService {

    public static final String SYSTEM_ACTOR = "system";
    public static final String ENTITY_WORKFLOW = "WORKFLOW";

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, String details) {
        String safeDetails = details != null && details.length() > 2000 ? details.substring(0, 2000) : details;
        repository.save(new AuditEvent(currentActor(), action, entityType, entityId, safeDetails,
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> history(String entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAt(entityType, entityId);
    }

    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return SYSTEM_ACTOR;
        }
        return auth.getName();
    }
}
