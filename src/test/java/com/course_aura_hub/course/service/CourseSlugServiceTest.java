package com.course_aura_hub.course.service;

import com.course_aura_hub.course.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSlugServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseSlugService courseSlugService;

    @Test
    void generateUniqueSlug_slugifiesTitle() {
        when(courseRepository.existsBySlug("nuxt-3-mastery-from-zero-to-hero")).thenReturn(false);

        String slug = courseSlugService.generateUniqueSlug("Nuxt 3 Mastery: From Zero to Hero");

        assertThat(slug).isEqualTo("nuxt-3-mastery-from-zero-to-hero");
    }

    @Test
    void generateUniqueSlug_appendsSuffixWhenSlugExists() {
        when(courseRepository.existsBySlug("nuxt-3-mastery")).thenReturn(true);
        when(courseRepository.existsBySlug("nuxt-3-mastery-1")).thenReturn(false);

        String slug = courseSlugService.generateUniqueSlug("Nuxt 3 Mastery");

        assertThat(slug).isEqualTo("nuxt-3-mastery-1");
    }

    @Test
    void generateUniqueSlug_handlesEmptyTitle() {
        when(courseRepository.existsBySlug("course")).thenReturn(false);

        String slug = courseSlugService.generateUniqueSlug("!!!");

        assertThat(slug).isEqualTo("course");
    }
}
