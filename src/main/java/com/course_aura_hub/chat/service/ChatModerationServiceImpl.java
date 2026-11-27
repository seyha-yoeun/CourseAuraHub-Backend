package com.course_aura_hub.chat.service;

import com.course_aura_hub.audit.model.AdminAuditEvent;
import com.course_aura_hub.audit.model.AuditOutcome;
import com.course_aura_hub.audit.service.AdminAuditService;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.model.ChatConversation;
import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.model.ChatRestriction;
import com.course_aura_hub.chat.model.ConversationStatus;
import com.course_aura_hub.chat.model.ConversationType;
import com.course_aura_hub.chat.model.MessageKind;
import com.course_aura_hub.chat.repository.ChatConversationRepository;
import com.course_aura_hub.chat.repository.ChatMessageRepository;
import com.course_aura_hub.chat.repository.ChatRestrictionRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ResourceNotFoundException;
import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.common.util.DisplayNames;
import com.course_aura_hub.course.model.Course;
import com.course_aura_hub.course.repository.CourseRepository;
import com.course_aura_hub.moderation.model.ContentReport;
import com.course_aura_hub.moderation.model.ReportTargetType;
import com.course_aura_hub.moderation.repository.ContentReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModerationServiceImpl implements ChatModerationService {

    private static final int CONTEXT_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatRestrictionRepository restrictionRepository;
    private final ContentReportRepository reportRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ChatAccess access;
    private final ChatViews views;
    private final ChatPeople people;
    private final AdminAuditService auditService;
    private final ApplicationEventPublisher events;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.SupportPage supportThreads(String status, int page, int size) {
        ConversationStatus wanted = "closed".equalsIgnoreCase(status) ? ConversationStatus.CLOSED : ConversationStatus.OPEN;
        int pageSize = Math.max(1, Math.min(size <= 0 ? 20 : size, MAX_PAGE_SIZE));
        int pageNumber = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource("status", wanted.name())
                .addValue("limit", pageSize).addValue("offset", (long) pageNumber * pageSize);
        Long total = jdbc.queryForObject("select count(*) from chat_conversations where type = 'SUPPORT' and status = :status",
                params, Long.class);
        List<Integer> ids = jdbc.queryForList("""
                select id from chat_conversations where type = 'SUPPORT' and status = :status
                order by last_activity_at desc, id desc limit :limit offset :offset
                """, params, Integer.class);
        List<ChatDtos.SupportThread> items = new ArrayList<>();
        for (Integer id : ids) {
            conversationRepository.findById(id).ifPresent(conversation -> items.add(supportThread(conversation)));
        }
        return new ChatDtos.SupportPage(items, pageNumber, pageSize, total == null ? 0 : total);
    }

    @Override
    @Transactional
    public ChatDtos.Conversation closeSupport(Actor admin, Integer conversationId) {
        ChatConversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .filter(found -> found.getType() == ConversationType.SUPPORT)
                .orElseThrow(() -> ChatAccess.notFound(conversationId));
        if (conversation.getStatus() == ConversationStatus.OPEN) {
            ChatMessage line = messageRepository.saveAndFlush(ChatMessage.builder()
                    .conversationId(conversationId)
                    .kind(MessageKind.SYSTEM)
                    .body("Support closed this conversation")
                    .build());
            conversation.setStatus(ConversationStatus.CLOSED);
            conversation.setLastMessageId(line.getId());
            conversation.setLastActivityAt(line.getCreatedAt());
            conversationRepository.save(conversation);
            events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_CREATED, conversationId, line.getId(), admin.userId()));
            events.publishEvent(ChatSignal.conversation(ChatSignal.Kind.CONVERSATION_UPDATED, conversationId, admin.userId(), null));
            log.info("Support conversation {} closed by {}", conversationId, admin.userId());
        }
        return views.conversation(admin.userId(), true, access.requireReadable(admin, conversation));
    }

    @Override
    @Transactional
    public ChatDtos.ReportContext reportContext(Actor admin, Integer reportId, String clientIp) {
        ContentReport report = reportRepository.findById(reportId)
                .filter(found -> found.getTargetType() == ReportTargetType.CHAT_MESSAGE)
                .orElseThrow(() -> ResourceNotFoundException.of("Report", reportId));
        ChatMessage message = messageRepository.findById(report.getTargetId())
                .orElseThrow(() -> ResourceNotFoundException.of("Message", report.getTargetId()));
        ChatConversation conversation = conversationRepository.findById(message.getConversationId())
                .orElseThrow(() -> ChatAccess.notFound(message.getConversationId()));
        Integer conversationId = conversation.getId();
        List<ChatMessage> before = new ArrayList<>(messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(
                conversationId, message.getId(), PageRequest.of(0, CONTEXT_SIZE)));
        Collections.reverse(before);
        List<ChatMessage> after = messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(
                conversationId, message.getId(), PageRequest.of(0, CONTEXT_SIZE));
        List<ChatMessage> window = new ArrayList<>(before);
        window.add(message);
        window.addAll(after);
        Course course = conversation.getCourseId() == null ? null : courseRepository.findById(conversation.getCourseId()).orElse(null);

        auditService.record(AdminAuditEvent.builder()
                .actorId(admin.userId())
                .actorEmail(admin.email())
                .actorRole("ADMIN")
                .action("ChatAdminController.reportContext")
                .httpMethod("GET")
                .pathPattern("/api/v1/admin/chat/reports/{reportId}/context")
                .resource("chat")
                .resourceId(reportId.toString())
                .pathVariables("{\"reportId\":\"" + reportId + "\",\"conversationId\":\"" + conversationId + "\"}")
                .status(200)
                .outcome(AuditOutcome.SUCCESS)
                .requestId(MDC.get("requestId"))
                .ipAddress(clientIp)
                .build());
        log.info("Admin {} viewed chat report {} (conversation {})", admin.userId(), reportId, conversationId);
        return new ChatDtos.ReportContext(reportId, conversationId, ChatViews.lower(conversation.getType()),
                course == null ? null : new ChatDtos.CourseRef(course.getId(), course.getSlug(), course.getTitle()),
                message.getId(), views.messages(admin.userId(), window),
                views.editHistory(window.stream().map(ChatMessage::getId).toList()));
    }

    @Override
    @Transactional
    public ChatDtos.Restriction restrict(Actor admin, UUID userId, ChatDtos.RestrictionRequest request) {
        if (admin.userId().equals(userId)) {
            throw new BusinessRuleException("You can't restrict yourself");
        }
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("User", userId);
        }
        if (!request.until().isAfter(Instant.now())) {
            throw new BusinessRuleException("The end must be in the future");
        }
        ChatRestriction restriction = restrictionRepository.findById(userId)
                .orElseGet(() -> ChatRestriction.builder().userId(userId).build());
        restriction.setUntil(request.until());
        restriction.setReason(request.reason().trim());
        restriction.setCreatedBy(admin.userId());
        restriction.setCreatedAt(Instant.now());
        restrictionRepository.save(restriction);
        log.info("Chat restricted for {} until {} by {}", userId, request.until(), admin.userId());
        return new ChatDtos.Restriction(userId, restriction.getUntil(), restriction.getReason(), restriction.getCreatedAt());
    }

    @Override
    @Transactional
    public void lift(Actor admin, UUID userId) {
        restrictionRepository.findById(userId).ifPresent(restriction -> {
            restrictionRepository.delete(restriction);
            log.info("Chat restriction lifted for {} by {}", userId, admin.userId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void requireReportable(Actor reporter, Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        access.requireReadable(reporter, message.getConversationId());
        if (message.getKind() != MessageKind.TEXT) {
            throw new BusinessRuleException("Only messages people wrote can be reported");
        }
        if (reporter.userId().equals(message.getSenderId())) {
            throw new BusinessRuleException("You can't report your own message");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTarget reportTarget(Integer messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", messageId));
        ChatConversation conversation = conversationRepository.findById(message.getConversationId()).orElse(null);
        String slug = conversation == null || conversation.getCourseId() == null ? null
                : courseRepository.findById(conversation.getCourseId()).map(Course::getSlug).orElse(null);
        String author = message.getSenderId() == null ? ChatPeople.DELETED_NAME
                : userRepository.findById(message.getSenderId())
                .map(user -> DisplayNames.fullName(user.getFirstName(), user.getLastName()))
                .orElse(ChatPeople.DELETED_NAME);
        String text = message.getDeletedAt() != null ? "Message deleted" : message.getBody() == null ? "Sent a file" : message.getBody();
        return new ReportTarget(slug, text, author, message.getHiddenAt() != null);
    }

    @Override
    @Transactional
    public void hideReported(Integer messageId, UUID adminId) {
        messageRepository.findById(messageId).ifPresent(message -> {
            if (message.getHiddenAt() == null) {
                message.setHiddenAt(Instant.now());
                message.setHiddenBy(adminId);
                messageRepository.save(message);
                events.publishEvent(ChatSignal.message(ChatSignal.Kind.MESSAGE_UPDATED, message.getConversationId(), messageId, adminId));
            }
        });
    }

    private ChatDtos.SupportThread supportThread(ChatConversation conversation) {
        ChatDtos.SupportRequester requester = conversation.getCreatedBy() == null ? null
                : userRepository.findById(conversation.getCreatedBy())
                .map(user -> new ChatDtos.SupportRequester(user.getId(),
                        DisplayNames.fullName(user.getFirstName(), user.getLastName()), user.getEmail()))
                .orElse(null);
        ChatMessage last = conversation.getLastMessageId() == null ? null
                : messageRepository.findById(conversation.getLastMessageId()).orElse(null);
        Map<UUID, ChatDtos.Person> named = last == null || last.getSenderId() == null ? Map.of()
                : people.lookup(conversation.getCreatedBy(), java.util.List.of(last.getSenderId()));
        return new ChatDtos.SupportThread(conversation.getId(), conversation.getStatus().name().toLowerCase(Locale.ROOT),
                requester, last == null ? null : views.lastMessage(last, named), conversation.getCreatedAt(),
                conversation.getLastActivityAt());
    }
}
