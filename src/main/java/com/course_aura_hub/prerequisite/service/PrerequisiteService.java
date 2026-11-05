package com.course_aura_hub.prerequisite.service;

import com.course_aura_hub.common.security.Actor;
import com.course_aura_hub.prerequisite.dto.PrerequisiteDtos;

import java.util.List;

public interface PrerequisiteService {

    /** A published course's prerequisites, with the viewer's completion when signed in. */
    PrerequisiteDtos.Requirements find(String slug, String viewerEmail);

    List<PrerequisiteDtos.Prerequisite> replace(Actor actor, String slug, List<PrerequisiteDtos.Item> items);

    /** Rejects enrolling or buying while a hard prerequisite is incomplete. */
    void requireMet(Integer studentId, Integer courseId);
}
