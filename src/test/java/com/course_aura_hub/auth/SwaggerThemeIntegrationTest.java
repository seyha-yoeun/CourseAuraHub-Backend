package com.course_aura_hub.auth;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API docs load their dark theme and light/dark switch, and anyone can fetch them, like the docs themselves. */
class SwaggerThemeIntegrationTest extends AuthIntegrationTestSupport {

    @Test
    void swaggerInitializer_loadsTheTheme_andKeepsSpringdocsConfig() throws Exception {
        mockMvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                // springdoc's own rewrite still happened (the API's grouped definitions, not the petstore)
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")))
                .andExpect(content().string(containsString("../swagger-theme/swagger-dark.css")))
                .andExpect(content().string(containsString("../swagger-theme/swagger-theme.js")));
    }

    @Test
    void themeFiles_arePublic() throws Exception {
        mockMvc.perform(get("/swagger-theme/swagger-dark.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-theme='dark'")));
        mockMvc.perform(get("/swagger-theme/swagger-theme.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cah-theme-toggle")));
    }
}
