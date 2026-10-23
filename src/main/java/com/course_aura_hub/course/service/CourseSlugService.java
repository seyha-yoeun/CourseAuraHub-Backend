package com.course_aura_hub.course.service;

import com.course_aura_hub.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseSlugService {

    private final CourseRepository courseRepository;

    public String generateUniqueSlug(String title) {
        String baseSlug = slugify(title);
        if (!StringUtils.hasText(baseSlug)) {
            baseSlug = "course";
        }

        String candidate = baseSlug;
        int suffix = 1;
        while (courseRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix++;
        }

        log.debug("Generated course slug={} from title={}", candidate, title);
        return candidate;
    }

    String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
