package com.course_aura_hub.audit.repository;

import com.course_aura_hub.audit.model.AdminAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdminAuditEventRepository
        extends JpaRepository<AdminAuditEvent, Long>, JpaSpecificationExecutor<AdminAuditEvent> {
}
