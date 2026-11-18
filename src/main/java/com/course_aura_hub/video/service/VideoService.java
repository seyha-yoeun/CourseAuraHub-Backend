package com.course_aura_hub.video.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.video.dto.VideoDtos;

/**
 * Playback for video lessons: the HLS stream, captions and transcripts. Everything here is only for viewers who
 * can watch the lesson: a free preview, its course's staff, or an enrolled learner it has opened for.
 */
public interface VideoService {

    VideoDtos.Media media(Actor actor, Integer lessonId);

    /** The HLS playlist with every segment replaced by a short-lived signed link. */
    String playlist(Actor actor, Integer lessonId);

    /** @param language such as en or km */
    VideoDtos.Transcript transcript(Actor actor, Integer lessonId, String language);

    VideoDtos.Caption saveCaption(Actor actor, String courseSlug, Integer lessonId, String language,
                                  VideoDtos.CaptionRequest request);

    void deleteCaption(Actor actor, String courseSlug, Integer lessonId, String language);
}
