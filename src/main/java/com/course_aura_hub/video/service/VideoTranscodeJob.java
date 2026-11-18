package com.course_aura_hub.video.service;

import com.course_aura_hub.common.scheduling.ClusterJobLock;
import com.course_aura_hub.video.config.VideoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Transcodes queued videos one after another, on one instance at a time, when transcoding is enabled. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeJob {

    private static final Duration RUN_BUDGET = Duration.ofMinutes(20);

    private final TranscodeQueue queue;
    private final VideoTranscoder transcoder;
    private final VideoProperties properties;
    private final ClusterJobLock jobLock;

    @Scheduled(fixedDelayString = "${app.video.poll-interval:60000}")
    public void run() {
        if (!properties.transcodingEnabled()) {
            return;
        }
        Duration lease = properties.timeout().plus(RUN_BUDGET).plusMinutes(5);
        jobLock.runExclusively("video-transcode", lease, Duration.ZERO, this::drain);
    }

    void drain() {
        queue.resetStale();
        queue.enqueueNew();
        Instant stop = Instant.now().plus(RUN_BUDGET);
        Optional<TranscodeQueue.Job> next;
        while (Instant.now().isBefore(stop) && (next = queue.claimNext()).isPresent()) {
            process(next.get());
        }
    }

    private void process(TranscodeQueue.Job job) {
        String sourceKey = job.sourceRef().substring(TranscodeQueue.STORAGE_PREFIX.length());
        String folder = "private/hls/" + job.lessonId() + "/" + job.id();
        try {
            VideoTranscoder.Result result = transcoder.transcode(sourceKey, folder);
            queue.complete(job, result.playlistKey(), result.durationSeconds());
            log.info("Transcoded lessonId={} jobId={} seconds={}", job.lessonId(), job.id(), result.durationSeconds());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            queue.fail(job, "Interrupted");
        } catch (Exception ex) {
            log.warn("Transcode failed lessonId={} jobId={}: {}", job.lessonId(), job.id(), ex.getMessage());
            queue.fail(job, ex.getMessage());
        }
    }
}
