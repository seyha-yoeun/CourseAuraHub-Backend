package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.config.ChatProperties;
import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.chat.model.ChatMessage;
import com.course_aura_hub.chat.repository.ChatConversationRepository;
import com.course_aura_hub.chat.repository.ChatMessageRepository;
import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.security.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatSearchServiceImpl implements ChatSearchService {

    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 100;
    private static final String TOO_FAST = "You're searching too quickly. Wait a moment and try again.";

    private final ChatMessageRepository messageRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatAccess access;
    private final ChatViews views;
    private final ChatRateLimiter rateLimiter;
    private final ChatProperties properties;
    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public ChatDtos.SearchPage search(Actor actor, ChatDtos.SearchRequest request) {
        UUID me = actor.userId();
        String term = request.q() == null ? "" : request.q().strip();
        String has = request.has() == null || request.has().isBlank() ? null : request.has().strip().toLowerCase(Locale.ROOT);
        if (has != null && !List.of("file", "image", "link").contains(has)) {
            throw new BusinessRuleException("has must be file, image or link");
        }
        // Text is needed unless a "has" filter alone says what to look for (files have no text to match)
        if (term.length() < MIN_QUERY && has == null) {
            throw new BusinessRuleException("Type at least " + MIN_QUERY + " characters to search");
        }
        if (term.length() > MAX_QUERY) {
            term = term.substring(0, MAX_QUERY);
        }
        rateLimiter.check("chat-search", me, properties.searchesPerMinute(), TOO_FAST);

        int size = ChatServiceImpl.clamp(request.limit(), 20);
        MapSqlParameterSource params = new MapSqlParameterSource("me", me).addValue("limit", size + 1);
        StringBuilder filters = new StringBuilder();
        if (!term.isEmpty()) {
            params.addValue("pattern", "%" + escapeLike(term) + "%");
            filters.append(" and m.body ilike :pattern");
        }
        if (request.conversationId() != null) {
            params.addValue("conversationId", request.conversationId());
            filters.append(" and m.conversation_id = :conversationId");
        }
        if (request.senderId() != null) {
            params.addValue("senderId", request.senderId());
            filters.append(" and m.sender_id = :senderId");
        }
        if ("file".equals(has)) {
            filters.append(" and exists (select 1 from chat_attachments a where a.message_id = m.id)");
        } else if ("image".equals(has)) {
            filters.append(" and exists (select 1 from chat_attachments a where a.message_id = m.id and a.content_type like 'image/%')");
        } else if ("link".equals(has)) {
            filters.append(" and m.body ~* '(https?://|www\\.)'");
        }
        if (request.from() != null) {
            params.addValue("from", Timestamp.from(request.from()));
            filters.append(" and m.created_at >= :from");
        }
        if (request.to() != null) {
            params.addValue("to", Timestamp.from(request.to()));
            filters.append(" and m.created_at < :to");
        }
        if (request.before() != null) {
            params.addValue("before", request.before());
            filters.append(" and m.id < :before");
        }
        // Built by joining strings, not String.formatted: the filters hold % signs
        String sql = "select m.id"
                + " from chat_messages m"
                + " join chat_participants p on p.conversation_id = m.conversation_id and p.user_id = :me and p.left_at is null"
                + " join chat_conversations cv on cv.id = m.conversation_id"
                + " where m.kind = 'TEXT' and m.deleted_at is null and m.hidden_at is null"
                + " and not exists (select 1 from chat_message_hidden_for h where h.message_id = m.id and h.user_id = :me)"
                + " and (cv.type not in ('COURSE', 'GROUP')"
                + "      or exists (select 1 from users u, courses c where u.id = :me and c.id = cv.course_id and "
                + ChatEligibility.ELIGIBLE_SQL + "))"
                + filters
                + " order by m.id desc limit :limit";
        List<Integer> ids = jdbc.queryForList(sql, params, Integer.class);
        boolean more = ids.size() > size;
        if (more) {
            ids = ids.subList(0, size);
        }

        Map<Integer, ChatMessage> byId = messageRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        List<ChatMessage> found = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        Map<Integer, ChatDtos.ConversationRef> conversations = new HashMap<>();
        for (Integer conversationId : found.stream().map(ChatMessage::getConversationId).collect(Collectors.toCollection(LinkedHashSet::new))) {
            conversationRepository.findById(conversationId).ifPresent(conversation -> conversations.put(conversationId,
                    views.conversationRef(me, actor.admin(), access.requireReadable(actor, conversation))));
        }
        List<ChatDtos.SearchHit> hits = new ArrayList<>(found.size());
        for (ChatDtos.Message message : views.messages(me, found)) {
            ChatDtos.ConversationRef conversation = conversations.get(message.conversationId());
            if (conversation != null) {
                hits.add(new ChatDtos.SearchHit(message, conversation));
            }
        }
        return new ChatDtos.SearchPage(hits, more && !ids.isEmpty() ? String.valueOf(ids.getLast()) : null);
    }

    /** The text as an ILIKE pattern piece: its own % and _ match themselves. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
