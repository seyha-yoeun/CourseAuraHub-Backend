package com.course_aura_hub.video.service;

import com.course_aura_hub.video.config.VideoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** The transcode jobs table. Each step is its own short transaction; FFmpeg runs between them. */
@Component
@RequiredArgsConstructor
public class TranscodeQueue {

    static final String STORAGE_PREFIX = "storage://";

    private final JdbcTemplate jdbcTemplate;
    private final VideoProperties properties;

    public record Job(Integer id, Integer lessonId, String sourceRef) {
    }

    /** Queues every video lesson whose uploaded file has no stream yet. @return how many */
    @Transactional
    public int enqueueNew() {
        int queued = jdbcTemplate.update("""
                insert into video_transcode_jobs (lesson_id, source_ref, status)
                select l.id, l.content_url, 'QUEUED' from lessons l
                where l.type = 'VIDEO' and l.content_url like 'storage://%'
                  and coalesce(l.video_source_ref, '') <> l.content_url
                  and not exists (select 1 from video_transcode_jobs j
                                  where j.lesson_id = l.id and j.source_ref = l.content_url
                                    and (j.status in ('QUEUED', 'RUNNING') or (j.status = 'FAILED' and j.attempts >= ?)))
                on conflict do nothing
                """, properties.maxAttempts());
        jdbcTemplate.update("""
                update lessons l set video_status = 'PROCESSING'
                where exists (select 1 from video_transcode_jobs j where j.lesson_id = l.id
                              and j.source_ref = l.content_url and j.status in ('QUEUED', 'RUNNING'))
                  and coalesce(l.video_status, '') <> 'PROCESSING'
                """);
        return queued;
    }

    /** Puts back jobs whose instance died mid-transcode. */
    @Transactional
    public int resetStale() {
        return jdbcTemplate.update("""
                update video_transcode_jobs set status = 'QUEUED', updated_at = now()
                where status = 'RUNNING' and updated_at < now() - make_interval(secs => ?)
                """, properties.timeout().toSeconds() + 600);
    }

    @Transactional
    public Optional<Job> claimNext() {
        List<Job> jobs = jdbcTemplate.query("""
                update video_transcode_jobs set status = 'RUNNING', attempts = attempts + 1, updated_at = now()
                where id = (select id from video_transcode_jobs where status = 'QUEUED'
                            order by created_at, id limit 1 for update skip locked)
                returning id, lesson_id, source_ref
                """, (rs, i) -> new Job(rs.getInt("id"), rs.getInt("lesson_id"), rs.getString("source_ref")));
        return jobs.stream().findFirst();
    }

    /**
     * Points the lesson at its new stream, sets its duration from the video and refreshes the course's hours.
     * Skipped when the lesson's file changed during the transcode; the new file gets its own job.
     */
    @Transactional
    public void complete(Job job, String playlistKey, int durationSeconds) {
        jdbcTemplate.update("update video_transcode_jobs set status = 'DONE', error = null, updated_at = now() where id = ?",
                job.id());
        int updated = jdbcTemplate.update("""
                update lessons set hls_manifest_ref = ?, video_source_ref = content_url, video_status = 'READY',
                       duration_seconds = ?, duration_minutes = greatest(1, ceil(? / 60.0))
                where id = ? and content_url = ?
                """, STORAGE_PREFIX + playlistKey, durationSeconds, durationSeconds, job.lessonId(), job.sourceRef());
        if (updated == 1) {
            jdbcTemplate.update("""
                    update courses c set total_hours = round((
                        select coalesce(sum(l.duration_minutes), 0) from lessons l
                        join course_sections s on s.id = l.section_id where s.course_id = c.id) / 60.0, 2)
                    where c.id = (select s.course_id from lessons l join course_sections s on s.id = l.section_id
                                  where l.id = ?)
                    """, job.lessonId());
        }
    }

    @Transactional
    public void fail(Job job, String error) {
        String message = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 1000));
        jdbcTemplate.update("""
                update video_transcode_jobs
                set status = case when attempts >= ? then 'FAILED' else 'QUEUED' end, error = ?, updated_at = now()
                where id = ?
                """, properties.maxAttempts(), message, job.id());
        jdbcTemplate.update("""
                update lessons set video_status = 'FAILED' where id = ? and content_url = ?
                  and exists (select 1 from video_transcode_jobs where id = ? and status = 'FAILED')
                """, job.lessonId(), job.sourceRef(), job.id());
    }
}
