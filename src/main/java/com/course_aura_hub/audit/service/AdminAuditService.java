package com.course_aura_hub.audit.service;

import com.course_aura_hub.audit.dto.AdminAuditPageResponse;
import com.course_aura_hub.audit.dto.AdminAuditQuery;
import com.course_aura_hub.audit.model.AdminAuditEvent;

public interface AdminAuditService {

    /** Saves an event in its own transaction. Never throws, so auditing can't fail a request. */
    void record(AdminAuditEvent event);

    AdminAuditPageResponse search(AdminAuditQuery query, int page, int size);
}
