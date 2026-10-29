package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves the real client IP. {@code X-Forwarded-For} is honoured only when the direct peer is a
 * configured trusted proxy, and then the right-most untrusted hop wins, so clients cannot spoof
 * their address by sending the header themselves.
 */
@Component
public class ClientInfoResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final int MAX_USER_AGENT_LENGTH = 255;
    // IP literals only: anything else could make InetAddress perform a DNS lookup
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientInfoResolver(AuthSecurityProperties properties) {
        this.trustedProxies = properties.trustedProxies().stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (!StringUtils.hasText(forwardedFor)) {
            return remoteAddress;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!isIpLiteral(hop)) {
                // Malformed chain: stop trusting it rather than guessing
                return remoteAddress;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return hops[0].trim();
    }

    /** Metadata of the request bound to the current thread, or {@link ClientInfo#UNKNOWN}. */
    public ClientInfo current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return ClientInfo.UNKNOWN;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return new ClientInfo(resolveClientIp(request), truncate(request.getHeader(HttpHeaders.USER_AGENT)));
    }

    private boolean isTrustedProxy(String address) {
        if (trustedProxies.isEmpty() || !isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpLiteral(String value) {
        return value != null && IP_LITERAL.matcher(value).matches();
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT_LENGTH ? userAgent : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
