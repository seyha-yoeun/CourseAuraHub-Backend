package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.exception.OAuth2LoginException;
import com.course_aura_hub.auth.security.oauth2.OAuth2UserInfoExtractorRegistry;
import com.course_aura_hub.auth.service.LoginOutcome;
import com.course_aura_hub.auth.service.OAuth2Service;
import jakarta.servlet.ServletException;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2Service oAuth2Service;
    private final OAuth2UserInfoExtractorRegistry extractorRegistry;
    private final AuthCookieService authCookieService;

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
        clearAuthenticationAttributes(request);

        String targetUrl;
        try {
            OAuth2UserDto profile = extractorRegistry.extract(registrationId, oAuth2User.getAttributes());
            targetUrl = switch (oAuth2Service.processOAuth2Login(profile)) {
                case LoginOutcome.Authenticated authenticated -> {
                    // Tokens travel only in HttpOnly cookies, never in the redirect URL
                    authCookieService.setAuthCookies(response, authenticated.auth());
                    yield redirectUri;
                }
                case LoginOutcome.MfaRequired mfa -> {
                    authCookieService.setMfaChallengeCookie(response, mfa.challenge());
                    yield UriComponentsBuilder.fromUriString(redirectUri).queryParam("mfa", "required").build().toUriString();
                }
            };
        } catch (OAuth2LoginException ex) {
            targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("error", ex.getReason().code())
                    .build()
                    .toUriString();
        }
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
