package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.CookieProperties;
import com.course_aura_hub.auth.exception.TokenExpiredException;
import com.course_aura_hub.auth.service.RefreshSessionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final CookieProperties cookieProperties;
    private final RefreshSessionService refreshSessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = resolveToken(request);

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtTokenProvider.extractAllClaims(jwt);
            String tokenType = claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class);

            if (!JwtTokenProvider.ACCESS_TOKEN_TYPE.equalsIgnoreCase(tokenType)) {
                log.warn("Rejected non-access token on request: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            String userEmail = claims.getSubject();

            if (StringUtils.hasText(userEmail) && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                UUID sessionId = JwtTokenProvider.sessionIdOf(claims);

                if (!(userDetails instanceof AuthUserPrincipal principal)) {
                    log.warn("Rejected access token with unexpected principal type");
                } else if (isRevoked(claims, principal)) {
                    log.warn("Rejected revoked access token for user: {}", userEmail);
                } else if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
                    log.warn("Rejected access token for disabled, unverified or locked user: {}", userEmail);
                } else if (sessionId != null && !refreshSessionService.isSessionActive(sessionId)) {
                    log.warn("Rejected access token of a revoked session for user: {}", userEmail);
                } else {
                    AuthUserPrincipal sessionPrincipal = principal.withSessionId(sessionId);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            sessionPrincipal,
                            null,
                            sessionPrincipal.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (TokenExpiredException ex) {
            log.debug("Expired JWT on request: {}", request.getRequestURI());
        } catch (Exception ex) {
            log.warn("Cannot set user authentication from token: {}", ex.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRevoked(Claims claims, AuthUserPrincipal principal) {
        Object tokenVersion = claims.get(JwtTokenProvider.CLAIM_TOKEN_VERSION);
        return !(tokenVersion instanceof Number number) || number.intValue() != principal.getTokenVersion();
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String headerToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (StringUtils.hasText(headerToken)) {
                return headerToken;
            }
        }

        return getTokenFromCookie(request, cookieProperties.accessTokenName());
    }

    private String getTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
