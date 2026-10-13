package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.CookieProperties;
import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.service.OAuth2Service;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2Service oAuth2Service;
    private final CookieProperties cookieProperties;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/redirect}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        log.info("Processing OAuth2 login success for provider: {}", registrationId);

        // Normalize attributes and provision/update user in database
        OAuth2UserDto userDto = OAuth2UserDto.fromProvider(registrationId, oAuth2User.getAttributes());
        AuthResponse authResponse = oAuth2Service.processOAuth2PostLogin(userDto);

        // Set access token cookie
        Cookie accessTokenCookie = createCookie(
                cookieProperties.accessTokenName(),
                authResponse.accessToken(),
                (int) cookieProperties.accessTokenMaxAge()
        );
        response.addCookie(accessTokenCookie);

        // Set refresh token cookie
        Cookie refreshTokenCookie = createCookie(
                cookieProperties.refreshTokenName(),
                authResponse.refreshToken(),
                (int) cookieProperties.refreshTokenMaxAge()
        );
        response.addCookie(refreshTokenCookie);

        // Clean redirect to frontend (no tokens in URL)
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
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
        // SameSite attribute (requires Servlet API 3.1+ / Spring Boot 3+)
        cookie.setAttribute("SameSite", cookieProperties.sameSite());
        return cookie;
    }
}