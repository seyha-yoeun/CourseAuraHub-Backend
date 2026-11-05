package com.course_aura_hub.revision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Set;

/**
 * @param immediateFields fields an instructor may change on a live course without review, such as tags. A change
 *                        that touches only these fields applies straight away.
 */
@ConfigurationProperties(prefix = "app.course-revisions")
public record CourseRevisionProperties(
        @DefaultValue({"tags", "thumbnail"}) Set<String> immediateFields
) {
}
