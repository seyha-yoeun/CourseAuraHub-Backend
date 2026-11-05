package com.course_aura_hub.course;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogSearchIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String CATALOG = "/api/v1/courses";

    @Test
    void titleMatchesOutrankDescriptionMatches_andStemmingFindsWordForms() throws Exception {
        TestUser admin = registerAdmin();
        String token = "zq" + System.nanoTime();
        String inDescription = createCourse(admin, Map.of(
                "title", "Generic course " + token + "a", "description", "Covers deployment pipelines " + token));
        String inTitle = createCourse(admin, Map.of(
                "title", "Deployment pipelines " + token, "description", "Hands-on"));

        assertThat(search(Map.of("search", token))).containsExactly(inTitle, inDescription);
        // "pipeline" only matches "pipelines" through the stemmer
        assertThat(search(Map.of("search", "pipeline " + token))).containsExactly(inTitle, inDescription);
        // Partial words still match through the substring fallback
        assertThat(search(Map.of("search", token.substring(0, token.length() - 2)))).contains(inTitle, inDescription);
        // An explicit sort replaces relevance
        assertThat(search(Map.of("search", token, "sort", "title", "order", "asc"))).containsExactly(inTitle, inDescription);
    }

    @Test
    void tagsAreSearchable_andWildcardsAreLiteral() throws Exception {
        TestUser admin = registerAdmin();
        String tag = "tagged" + System.nanoTime();
        String course = createCourse(admin, Map.of("title", "Plain title", "tags", List.of(tag)));

        assertThat(search(Map.of("search", tag))).containsExactly(course);
        assertThat(search(Map.of("search", "%_%" + System.nanoTime()))).isEmpty();
    }

    @Test
    void filtersNarrowByLevelPriceAndFreeness() throws Exception {
        TestUser admin = registerAdmin();
        String token = "flt" + System.nanoTime();
        String freeBeginner = createCourse(admin, Map.of("title", "Free " + token, "level", "beginner", "price", 0));
        String paidAdvanced = createCourse(admin, Map.of(
                "title", "Paid " + token, "level", "advanced", "price", 49.99, "originalPrice", 49.99));

        assertThat(search(Map.of("search", token, "level", "advanced"))).containsExactly(paidAdvanced);
        assertThat(search(Map.of("search", token, "free", "true"))).containsExactly(freeBeginner);
        assertThat(search(Map.of("search", token, "free", "false"))).containsExactly(paidAdvanced);
        assertThat(search(Map.of("search", token, "minPrice", "10", "maxPrice", "50"))).containsExactly(paidAdvanced);
        assertThat(search(Map.of("search", token, "maxPrice", "10"))).containsExactly(freeBeginner);
        assertThat(search(Map.of("search", token, "onSale", "true"))).isEmpty();
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        mockMvc.perform(get(CATALOG).param("minPrice", "20").param("maxPrice", "10")).andExpect(status().isBadRequest());
        mockMvc.perform(get(CATALOG).param("minRating", "6")).andExpect(status().isBadRequest());
        mockMvc.perform(get(CATALOG).param("minHours", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(CATALOG).param("level", "expert")).andExpect(status().isBadRequest());
    }

    @Test
    void visitorsNeverFindUnpublishedCourses() throws Exception {
        TestUser admin = registerAdmin();
        String token = "hid" + System.nanoTime();
        createCourse(admin, Map.of("title", "Draft " + token, "status", "draft"));
        assertThat(search(Map.of("search", token))).isEmpty();
    }

    private List<String> search(Map<String, String> params) throws Exception {
        var request = get(CATALOG);
        params.forEach(request::param);
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[*].slug");
    }

    private String createCourse(TestUser admin, Map<String, Object> fields) throws Exception {
        Map<String, Object> body = new HashMap<>(fields);
        body.putIfAbsent("status", "published");
        String response = mockMvc.perform(asJson(admin, post(CATALOG), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.slug");
    }
}
