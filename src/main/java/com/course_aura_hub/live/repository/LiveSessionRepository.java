package com.course_aura_hub.live.repository;

import com.course_aura_hub.live.model.LiveSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Integer> {

    List<LiveSession> findByCourseIdOrderByStartsAtAsc(Integer courseId);

    /** Sessions in these courses that start before {@code until} and haven't ended by {@code from}. */
    @Query(value = """
            select * from live_sessions s
            where s.course_id in (:courseIds) and s.starts_at < :until
              and s.starts_at + make_interval(mins => s.duration_minutes) > :from
            order by s.starts_at
            """, nativeQuery = true)
    List<LiveSession> findInWindow(@Param("courseIds") Collection<Integer> courseIds, @Param("from") Instant from,
                                   @Param("until") Instant until);

    /** Upcoming sessions starting inside the reminder window, locked so two runs can't both remind. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from LiveSession s
            where s.cancelled = false and s.startsAt > :now and s.startsAt <= :until
            order by s.startsAt
            """)
    List<LiveSession> findStartingForUpdate(@Param("now") Instant now, @Param("until") Instant until);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LiveSession s where s.id = :id")
    Optional<LiveSession> findByIdForUpdate(@Param("id") Integer id);
}
