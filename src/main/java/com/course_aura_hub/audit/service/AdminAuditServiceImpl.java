package com.course_aura_hub.audit.service;

import com.course_aura_hub.audit.dto.AdminAuditEventResponse;
import com.course_aura_hub.audit.dto.AdminAuditPageResponse;
import com.course_aura_hub.audit.dto.AdminAuditQuery;
import com.course_aura_hub.audit.model.AdminAuditEvent;
import com.course_aura_hub.audit.model.AuditOutcome;
import com.course_aura_hub.audit.repository.AdminAuditEventRepository;
import com.course_aura_hub.audit.repository.AdminAuditSpecifications;
import com.course_aura_hub.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditEventRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AdminAuditEvent event) {
        try {
            repository.save(event);
        } catch (DataAccessException ex) {
            // The change itself already happened; losing its audit row must not turn it into an error
            log.error("Could not record admin audit event action={} path={}", event.getAction(), event.getPathPattern(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAuditPageResponse search(AdminAuditQuery query, int page, int size) {
        if (query.from() != null && query.to() != null && !query.from().isBefore(query.to())) {
            throw new BusinessRuleException("from must be before to");
        }
        Specification<AdminAuditEvent> specification = Specification.allOf(
                AdminAuditSpecifications.actor(query.actorEmail()),
                AdminAuditSpecifications.equalsField("resource", query.resource()),
                AdminAuditSpecifications.equalsField("resourceId", query.resourceId()),
                AdminAuditSpecifications.equalsField("action", query.action()),
                AdminAuditSpecifications.outcome(parseOutcome(query.outcome())),
                AdminAuditSpecifications.between(query.from(), query.to())
        );
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AdminAuditEvent> result = repository.findAll(specification, request);
        return new AdminAuditPageResponse(
                result.map(AdminAuditEventResponse::from).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private static AuditOutcome parseOutcome(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AuditOutcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("outcome must be success or failure");
        }
    }
}
