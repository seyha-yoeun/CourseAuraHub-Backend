package com.course_aura_hub.course;

import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicCatalogIntegrationTest extends LearnerIntegrationTestSupport {

    @Test
    void visitor_canBrowseCatalogWithoutSalesFigures() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").isNotEmpty())
                .andExpect(jsonPath("$.data[0].revenue").doesNotExist())
                .andExpect(jsonPath("$.data[0].monthlyRevenue").doesNotExist());

        mockMvc.perform(get("/api/v1/courses/" + FREE_COURSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(false))
                .andExpect(jsonPath("$.data.revenue").doesNotExist())
                .andExpect(jsonPath("$.data.completionRate").doesNotExist())
                .andExpect(jsonPath("$.data.monthlyEnrollments").doesNotExist())
                .andExpect(jsonPath("$.data.enrolledStudentList").doesNotExist());
    }

    @Test
    void visitor_seesOutlineButNotLockedContent() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + FREE_COURSE + "/curriculum"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").value(false))
                .andExpect(jsonPath("$.data.progress").doesNotExist())
                .andExpect(jsonPath("$.data.sections[0].lessons[0].contentUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.sections[0].lessons[1].locked").value(true))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].contentUrl").doesNotExist());

        mockMvc.perform(get("/api/v1/courses/" + FREE_COURSE + "/reviews"))
                .andExpect(status().isOk());
    }

    @Test
    void learner_doesNotReceiveSalesFiguresButAdminDoes() throws Exception {
        mockMvc.perform(as(registerUser(), get("/api/v1/courses/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue").doesNotExist());

        mockMvc.perform(as(registerAdmin(), get("/api/v1/courses/" + PAID_COURSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revenue").isNumber())
                .andExpect(jsonPath("$.data.monthlyRevenue").isArray());
    }

    @Test
    void visitor_cannotWriteOrReachLearnerEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/me/courses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/courses")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/courses/" + FREE_COURSE)).andExpect(status().isForbidden());
    }
}
