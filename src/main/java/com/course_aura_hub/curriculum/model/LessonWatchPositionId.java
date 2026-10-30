package com.course_aura_hub.curriculum.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LessonWatchPositionId implements Serializable {

    private Integer studentId;
    private Integer lessonId;
}
