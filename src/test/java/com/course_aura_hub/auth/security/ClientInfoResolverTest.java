package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientInfoResolverTest {

    @Test
    void ignoresForwardedForFromUntrustedPeers() {
        ClientInfoResolver resolver = new ClientInfoResolver(properties(List.of()));
        MockHttpServletRequest request = request("203.0.113.9", "1.2.3.4");
        assertThat(resolver.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void usesRightmostUntrustedHopBehindTrustedProxy() {
        ClientInfoResolver resolver = new ClientInfoResolver(properties(List.of("10.0.0.0/8")));
        // Client spoofed "1.2.3.4"; the proxy appended the real address 198.51.100.7
        MockHttpServletRequest request = request("10.0.0.5", "1.2.3.4, 198.51.100.7");
        assertThat(resolver.resolveClientIp(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void skipsChainedTrustedProxies() {
        ClientInfoResolver resolver = new ClientInfoResolver(properties(List.of("10.0.0.0/8", "172.16.0.1")));
        MockHttpServletRequest request = request("10.0.0.5", "198.51.100.7, 172.16.0.1");
        assertThat(resolver.resolveClientIp(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void malformedHeaderFallsBackToPeerAddress() {
        ClientInfoResolver resolver = new ClientInfoResolver(properties(List.of("10.0.0.0/8")));
        MockHttpServletRequest request = request("10.0.0.5", "evil.example.com");
        assertThat(resolver.resolveClientIp(request)).isEqualTo("10.0.0.5");
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    static AuthSecurityProperties properties(List<String> trustedProxies) {
        return new AuthSecurityProperties(
                new AuthSecurityProperties.EmailVerification(6, Duration.ofMinutes(10), 5, Duration.ofSeconds(60), 5),
                new AuthSecurityProperties.PasswordReset(Duration.ofMinutes(30), Duration.ofSeconds(60), 5, "http://localhost/reset"),
                new AuthSecurityProperties.Lockout(5, Duration.ofMinutes(15)),
                new AuthSecurityProperties.Session(10, Duration.ofDays(30), Duration.ofSeconds(10)),
                new AuthSecurityProperties.Mfa("Test", "", Duration.ofMinutes(5), 5, 10),
                new AuthSecurityProperties.OAuth2(false),
                Duration.ofDays(7),
                trustedProxies
        );
    }
}
