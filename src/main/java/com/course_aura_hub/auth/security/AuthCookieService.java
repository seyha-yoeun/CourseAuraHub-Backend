package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.AuthSecurityProperties;
import com.course_aura_hub.auth.config.CookieProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.MfaChallengeResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieService {

    public static final String MFA_CHALLENGE_COOKIE = "mfa_challenge";
    private static final String MFA_COOKIE_PATH = "/api/v1/auth/mfa";

    private final CookieProperties cookieProperties;
    private final AuthSecurityProperties authSecurityProperties;

    /**
     * Sets access and refresh cookies. The refresh cookie lives exactly as long as the refresh
     * token, so remember-me (30 days) survives every rotation.
     */
    public void setAuthCookies(HttpServletResponse response, AuthResponse authResponse) {
        int refreshTokenMaxAge = authResponse.refreshExpiresIn() > 0
                ? (int) (authResponse.refreshExpiresIn() / 1000)
                : (int) cookieProperties.refreshTokenMaxAge();

        response.addCookie(createCookie(
                cookieProperties.accessTokenName(),
                authResponse.accessToken(),
                (int) cookieProperties.accessTokenMaxAge(),
                cookieProperties.path()
        ));
        response.addCookie(createCookie(
                cookieProperties.refreshTokenName(),
                authResponse.refreshToken(),
                refreshTokenMaxAge,
                cookieProperties.path()
        ));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addCookie(createCookie(cookieProperties.accessTokenName(), "", 0, cookieProperties.path()));
        response.addCookie(createCookie(cookieProperties.refreshTokenName(), "", 0, cookieProperties.path()));
    }

    public String getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, cookieProperties.refreshTokenName());
    }

    /** Browser OAuth2 flow: the challenge travels in an HttpOnly cookie scoped to the MFA endpoint, never in the URL. */
    public void setMfaChallengeCookie(HttpServletResponse response, MfaChallengeResponse challenge) {
        int maxAge = (int) authSecurityProperties.mfa().challengeTtl().toSeconds();
        response.addCookie(createCookie(MFA_CHALLENGE_COOKIE, challenge.mfaToken(), maxAge, MFA_COOKIE_PATH));
    }

    public void clearMfaChallengeCookie(HttpServletResponse response) {
        response.addCookie(createCookie(MFA_CHALLENGE_COOKIE, "", 0, MFA_COOKIE_PATH));
    }

    public String getMfaChallenge(HttpServletRequest request) {
        return getCookieValue(request, MFA_CHALLENGE_COOKIE);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Cookie createCookie(String name, String value, int maxAge, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(cookieProperties.httpOnly());
        cookie.setSecure(cookieProperties.secure());
        cookie.setPath(path);
        cookie.setMaxAge(maxAge);
        if (cookieProperties.domain() != null && !cookieProperties.domain().isBlank()) {
            cookie.setDomain(cookieProperties.domain());
        }
        cookie.setAttribute("SameSite", cookieProperties.sameSite());
        return cookie;
    }
}
