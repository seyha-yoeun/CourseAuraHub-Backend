package com.course_aura_hub.certificate;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.certificates.sharing.api-base-url=https://api.example.com/api/v1/",
        "app.certificates.verify-url=https://example.com/certificates"
})
class CertificateSharingIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String API = "https://api.example.com/api/v1";

    @Test
    void theOpenBadgesDocumentsArePublic_andLinkToEachOther() throws Exception {
        TestUser learner = registerUser();
        String number = earnCertificate(learner);

        String body = mockMvc.perform(get("/api/v1/certificates/" + number.toLowerCase(Locale.ROOT) + "/badge.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Assertion"))
                .andExpect(jsonPath("$.id").value(API + "/certificates/" + number + "/badge.json"))
                .andExpect(jsonPath("$.badge").value(API + "/badges/courses/" + FREE_COURSE + ".json"))
                .andExpect(jsonPath("$.verification.type").value("HostedBadge"))
                .andExpect(jsonPath("$.evidence").value("https://example.com/certificates/" + number))
                .andExpect(jsonPath("$.recipient.hashed").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(learner.email());
        String identity = JsonPath.read(body, "$.recipient.identity");
        assertThat(identity).isEqualTo("sha256$" + sha256(learner.email().toLowerCase(Locale.ROOT) + number));

        mockMvc.perform(get("/api/v1/badges/courses/" + FREE_COURSE + ".json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BadgeClass"))
                .andExpect(jsonPath("$.issuer").value(API + "/badges/issuer.json"))
                .andExpect(jsonPath("$.image").isNotEmpty());
        mockMvc.perform(get("/api/v1/badges/issuer.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Issuer"))
                .andExpect(jsonPath("$.name").isNotEmpty());
    }

    @Test
    void unknownCertificatesAndCoursesWithoutCertificates_haveNoBadge() throws Exception {
        mockMvc.perform(get("/api/v1/certificates/CAH-0000-0000-0000-0000/badge.json")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/badges/courses/no-such-course.json")).andExpect(status().isNotFound());
    }

    @Test
    void theLinkedInLink_isOnlyForTheLearnerWhoEarnedIt() throws Exception {
        TestUser learner = registerUser();
        String number = earnCertificate(learner);

        String body = mockMvc.perform(as(learner, get("/api/v1/me/certificates/" + number + "/linkedin-url")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateNumber").value(number))
                .andExpect(jsonPath("$.data.verifyUrl").value("https://example.com/certificates/" + number))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.url");
        assertThat(url).startsWith("https://www.linkedin.com/profile/add?startTask=CERTIFICATION_NAME")
                .contains("certId=" + number)
                .contains("organizationName=");

        mockMvc.perform(as(registerUser(), get("/api/v1/me/certificates/" + number + "/linkedin-url")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/me/certificates/" + number + "/linkedin-url")).andExpect(status().isUnauthorized());
    }

    private String earnCertificate(TestUser learner) throws Exception {
        enroll(learner, FREE_COURSE);
        String curriculum = mockMvc.perform(as(learner, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> lessons = JsonPath.read(curriculum, "$.data.sections[*].lessons[*].id");
        for (Integer lessonId : lessons) {
            mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion"))).andExpect(status().isOk());
        }
        String body = mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[0].certificateNumber");
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
