package com.course_aura_hub.auth.config;

import com.course_aura_hub.auth.security.ClientInfoResolver;
import com.course_aura_hub.auth.security.ClientInfoResolverTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    @Test
    void spoofedForwardedFor_doesNotEscapeTheLimit() throws Exception {
        RateLimitingFilter filter = filter(1000);
        int allowed = 0;
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = loginRequest("203.0.113.9");
            request.addHeader("X-Forwarded-For", "10.0.0." + i);
            if (run(filter, request).getStatus() == 200) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(10);
    }

    @Test
    void limitedResponse_isJsonWithRetryAfter() throws Exception {
        RateLimitingFilter filter = filter(1000);
        MockHttpServletResponse response = null;
        for (int i = 0; i < 11; i++) {
            response = run(filter, loginRequest("198.51.100.1"));
        }
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("\"success\":false");
    }

    @Test
    void trackedClients_areBounded() throws Exception {
        RateLimitingFilter filter = filter(50);
        for (int i = 0; i < 500; i++) {
            run(filter, loginRequest("10." + (i / 250) + ".0." + (i % 250)));
        }
        assertThat(filter.trackedClientCount()).isLessThanOrEqualTo(50);
    }

    @Test
    void randomPaths_shareTheDefaultBucket() throws Exception {
        RateLimitingFilter filter = filter(1000);
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/random-" + i);
            request.setRemoteAddr("192.0.2.200");
            run(filter, request);
        }
        assertThat(filter.trackedClientCount()).isEqualTo(1);
    }

    private static RateLimitingFilter filter(int maxClients) {
        RateLimitProperties properties = new RateLimitProperties(true, maxClients,
                new RateLimitProperties.Rule(null, 100, Duration.ofMinutes(1)), Map.of());
        return new RateLimitingFilter(properties, new ClientInfoResolver(
                ClientInfoResolverTestSupport.properties(List.of())));
    }

    private static MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }

    private static MockHttpServletResponse run(RateLimitingFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
