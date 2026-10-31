package com.course_aura_hub.certificate;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CertificateIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String UNKNOWN_NUMBER = "CAH-0000-0000-0000-0000";

    private String pdfPath(String number) {
        return "/api/v1/me/certificates/" + number + "/pdf";
    }

    private List<Integer> lessonIds(TestUser user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/courses/" + FREE_COURSE + "/curriculum")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.sections[*].lessons[*].id");
    }

    private void complete(TestUser learner, Integer lessonId) throws Exception {
        mockMvc.perform(as(learner, put("/api/v1/me/lessons/" + lessonId + "/completion")))
                .andExpect(status().isOk());
    }

    private String earnCertificate(TestUser learner) throws Exception {
        enroll(learner, FREE_COURSE);
        for (Integer lessonId : lessonIds(learner)) {
            complete(learner, lessonId);
        }
        String body = mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data[0].certificateNumber");
    }

    @Test
    void unfinishedCourse_earnsNoCertificate() throws Exception {
        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        complete(learner, lessonIds(learner).get(0));

        mockMvc.perform(as(learner, get("/api/v1/me/certificates")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void download_returnsPdfWithCertificateDetails() throws Exception {
        TestUser learner = registerUser();
        String number = earnCertificate(learner);

        byte[] pdf = mockMvc.perform(as(learner, get(pdfPath(number))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString(number)))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("Jane Learner", "Git & GitHub Fundamentals", number, "/certificates/" + number);
        }
    }

    @Test
    void download_acceptsAnyCaseOfTheNumber() throws Exception {
        TestUser learner = registerUser();
        String number = earnCertificate(learner);

        mockMvc.perform(as(learner, get(pdfPath(number.toLowerCase()))))
                .andExpect(status().isOk());
    }

    @Test
    void download_isOnlyForTheLearnerWhoEarnedIt() throws Exception {
        String number = earnCertificate(registerUser());

        mockMvc.perform(as(registerUser(), get(pdfPath(number))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(pdfPath(number)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void download_rejectsUnknownAndMalformedNumbers() throws Exception {
        TestUser learner = registerUser();
        earnCertificate(learner);

        mockMvc.perform(as(learner, get(pdfPath(UNKNOWN_NUMBER)))).andExpect(status().isNotFound());
        mockMvc.perform(as(learner, get(pdfPath("not-a-number")))).andExpect(status().isNotFound());
    }

    @Test
    void certificate_keepsTheDetailsFromWhenItWasIssued() throws Exception {
        TestUser learner = registerUser();
        String number = earnCertificate(learner);

        jdbcTemplate.update(
                "update students set name = 'Renamed Learner' where id = (select student_id from course_certificates where certificate_number = ?)",
                number);

        mockMvc.perform(get("/api/v1/certificates/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learnerName").value("Jane Learner"))
                .andExpect(jsonPath("$.data.courseTitle").value("Git & GitHub Fundamentals"));
    }
}
