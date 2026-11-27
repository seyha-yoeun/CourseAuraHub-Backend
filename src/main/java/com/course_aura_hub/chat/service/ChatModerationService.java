package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.security.Actor;

import java.util.UUID;

/**
 * Admin tools for chat: the support inbox, chat restrictions, and the report view. A report is the only way an
 * admin can see into a direct conversation or a private group, and every look is written to the admin audit log.
 */
public interface ChatModerationService {

    /** The reported item as the moderation queue shows it. */
    record ReportTarget(String courseSlug, String text, String author, boolean hidden) {
    }

    ChatDtos.SupportPage supportThreads(String status, int page, int size);

    ChatDtos.Conversation closeSupport(Actor admin, Integer conversationId);

    /** The reported message with ten messages either side; records an audit event. */
    ChatDtos.ReportContext reportContext(Actor admin, Integer reportId, String clientIp);

    ChatDtos.Restriction restrict(Actor admin, UUID userId, ChatDtos.RestrictionRequest request);

    void lift(Actor admin, UUID userId);

    /** Refuses a report of a message the reporter can't read (404) or of a system line (400). */
    void requireReportable(Actor reporter, Integer messageId);

    ReportTarget reportTarget(Integer messageId);

    /** Hides a reported message after an admin upheld the report. */
    void hideReported(Integer messageId, UUID adminId);
}
