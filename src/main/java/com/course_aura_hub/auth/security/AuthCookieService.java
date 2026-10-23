package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.CookieProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieService {

    private static final int REMEMBER_ME_REFRESH_TOKEN_MAX_AGE = 30 * 24 * 60 * 60;

    private final CookieProperties cookieProperties;

    public void setAuthCookies(HttpServletResponse response, AuthResponse authResponse) {
        setAuthCookies(response, authResponse, false);
    }

    public void setAuthCookies(HttpServletResponse response, AuthResponse authResponse, boolean rememberMe) {
        int refreshTokenMaxAge = rememberMe
                ? REMEMBER_ME_REFRESH_TOKEN_MAX_AGE
                : (int) cookieProperties.refreshTokenMaxAge();

        response.addCookie(createCookie(
                cookieProperties.accessTokenName(),
                authResponse.accessToken(),
                (int) cookieProperties.accessTokenMaxAge()
        ));
        response.addCookie(createCookie(
                cookieProperties.refreshTokenName(),
                authResponse.refreshToken(),
                refreshTokenMaxAge
        ));
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addCookie(createCookie(cookieProperties.accessTokenName(), "", 0));
        response.addCookie(createCookie(cookieProperties.refreshTokenName(), "", 0));
    }

    public String getRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieProperties.refreshTokenName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Cookie createCookie(String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(cookieProperties.httpOnly());
        cookie.setSecure(cookieProperties.secure());
        cookie.setPath(cookieProperties.path());
        cookie.setMaxAge(maxAge);
        if (cookieProperties.domain() != null && !cookieProperties.domain().isBlank()) {
            cookie.setDomain(cookieProperties.domain());
        }
        cookie.setAttribute("SameSite", cookieProperties.sameSite());
        return cookie;
    }
}
