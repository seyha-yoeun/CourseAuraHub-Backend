package com.course_aura_hub.revision.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CourseRevisionProperties.class)
public class CourseRevisionConfig {
}
