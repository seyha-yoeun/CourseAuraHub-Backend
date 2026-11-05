package com.course_aura_hub.moderation.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.moderation.dto.ModerationDtos;

import java.util.UUID;

public interface ModerationService {

    /** One report per person per item; reporting again is a no-op. */
    void report(Actor actor, ModerationDtos.ReportRequest request);

    ModerationDtos.Page findOpen(int page, int size);

    /** Hides the reported item and closes every open report on it. */
    ModerationDtos.Report hide(Integer reportId, UUID adminId);

    /** Leaves the item visible and closes every open report on it. */
    ModerationDtos.Report dismiss(Integer reportId, UUID adminId);
}
