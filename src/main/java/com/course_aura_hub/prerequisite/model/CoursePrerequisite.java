package com.course_aura_hub.prerequisite.model;

import com.course_aura_hub.course.model.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** One course a learner should complete before another. */
@Entity
@Table(name = "course_prerequisites")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoursePrerequisite {

    @EmbeddedId
    private Id id;

    @MapsId("requiredCourseId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "required_course_id", nullable = false)
    private Course requiredCourse;

    /** Hard blocks enrolling until completed; soft only recommends. */
    @Column(name = "hard", nullable = false)
    private boolean hard;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "course_id", nullable = false)
        private Integer courseId;

        @Column(name = "required_course_id", nullable = false)
        private Integer requiredCourseId;
    }
}
