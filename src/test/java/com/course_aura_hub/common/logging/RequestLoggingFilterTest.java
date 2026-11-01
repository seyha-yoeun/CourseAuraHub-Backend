package com.course_aura_hub.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void logsThePathButNotTheQueryString() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.setQueryString("code=secret-oauth-code");

        run(request);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage())
                .isEqualTo("GET /login/oauth2/code/google 200 " + durationOf(appender.list.getFirst()) + "ms")
                .doesNotContain("secret-oauth-code");
    }

    private Object durationOf(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals("duration_ms"))
                .findFirst().orElseThrow().value;
    }

    @Test
    void keepsASafeRequestIdAndReturnsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "client-id-12345");

        MockHttpServletResponse response = run(request);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("client-id-12345");
        assertThat(appender.list.getFirst().getMDCPropertyMap())
                .containsEntry(RequestLoggingFilter.REQUEST_ID_KEY, "client-id-12345");
    }

    @Test
    void replacesAnUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "forged\nERROR fake log line");

        MockHttpServletResponse response = run(request);

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).hasSize(36);
    }

    @Test
    void reportsAnEscapingExceptionAsAServerErrorAndClearsTheRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/me/orders");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(appender.list.getFirst().getLevel().toString()).isEqualTo("ERROR");
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("POST /api/v1/me/orders 500");
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_KEY)).isNull();
    }

    @Test
    void skipsNoisyPaths() throws Exception {
        run(new MockHttpServletRequest("GET", "/swagger-ui/index.html"));

        assertThat(appender.list).isEmpty();
    }
}
