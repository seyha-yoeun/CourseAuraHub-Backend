package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.util.DisplayNames;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Names people the way a given viewer may see them. Staff (instructors and admins) are always shown in full and
 * see everyone in full; learners see other learners as "Jane L.". Emails are never part of a {@link ChatDtos.Person}.
 */
@Component
@RequiredArgsConstructor
public class ChatPeople {

    static final String DELETED_NAME = "Deleted user";
    /** A viewer id no account has, for names everyone may see. */
    private static final UUID NOBODY = new UUID(0L, 0L);

    private final NamedParameterJdbcTemplate jdbc;

    private record Row(UUID id, String firstName, String lastName, String imageUrl, boolean staff, boolean deleted) {
    }

    /** People by id as {@code viewerId} sees them; unknown ids are left out. */
    public Map<UUID, ChatDtos.Person> lookup(UUID viewerId, Collection<UUID> userIds) {
        Set<UUID> ids = new HashSet<>(userIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> query = new HashSet<>(ids);
        query.add(viewerId);
        List<Row> rows = jdbc.query("""
                        select id, first_name, last_name, image_url, role, enabled, email from users where id in (:ids)
                        """, new MapSqlParameterSource("ids", query),
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("first_name"), rs.getString("last_name"),
                        rs.getString("image_url"),
                        "ROLE_ADMIN".equals(rs.getString("role")) || "ROLE_INSTRUCTOR".equals(rs.getString("role")),
                        !rs.getBoolean("enabled") && rs.getString("email") != null
                                && rs.getString("email").endsWith("@deleted.invalid")));
        boolean viewerIsStaff = rows.stream().anyMatch(row -> row.id().equals(viewerId) && row.staff());
        Map<UUID, ChatDtos.Person> people = new HashMap<>();
        for (Row row : rows) {
            if (ids.contains(row.id())) {
                people.put(row.id(), toPerson(row, viewerIsStaff || row.id().equals(viewerId)));
            }
        }
        return people;
    }

    public ChatDtos.Person one(UUID viewerId, UUID userId) {
        return userId == null ? null : lookup(viewerId, List.of(userId)).get(userId);
    }

    /** The name everyone sees in system lines ("Jane L. joined"): short for learners, full for staff. */
    public String publicName(UUID userId) {
        ChatDtos.Person person = userId == null ? null : lookup(NOBODY, List.of(userId)).get(userId);
        return person == null ? DELETED_NAME : person.name();
    }

    private static ChatDtos.Person toPerson(Row row, boolean fullName) {
        if (row.deleted()) {
            return new ChatDtos.Person(row.id(), DELETED_NAME, null, false);
        }
        String full = DisplayNames.fullName(row.firstName(), row.lastName());
        String name = row.staff() || fullName ? full : DisplayNames.shortName(full);
        return new ChatDtos.Person(row.id(), name, StringUtils.hasText(row.imageUrl()) ? row.imageUrl() : null, row.staff());
    }
}
