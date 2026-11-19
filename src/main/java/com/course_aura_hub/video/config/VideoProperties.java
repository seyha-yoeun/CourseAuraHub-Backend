package com.course_aura_hub.video.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Video streaming. Transcoding needs FFmpeg and ffprobe on the server, so it is off until switched on; lessons
 * keep playing their original file until a stream is ready.
 *
 * @param transcodingEnabled run the background transcoder
 * @param ffmpegPath         ffmpeg executable
 * @param ffprobePath        ffprobe executable
 * @param segmentSeconds     length of each HLS segment
 * @param maxAttempts        tries before a transcode is left failed
 * @param timeout            longest a single transcode may run
 * @param linkTtl            how long signed playlist, segment and caption links last
 */
@ConfigurationProperties(prefix = "app.video")
public record VideoProperties(
        @DefaultValue("false") boolean transcodingEnabled,
        @DefaultValue("ffmpeg") String ffmpegPath,
        @DefaultValue("ffprobe") String ffprobePath,
        @DefaultValue("6") int segmentSeconds,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("2h") Duration timeout,
        @DefaultValue("3h") Duration linkTtl
) {
}
