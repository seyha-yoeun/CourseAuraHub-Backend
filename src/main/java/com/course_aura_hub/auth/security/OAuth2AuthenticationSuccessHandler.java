package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
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

        // Redirect to frontend client application with generated JWT tokens
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", authResponse.accessToken())
                .queryParam("refreshToken", authResponse.refreshToken())
                .queryParam("email", authResponse.email())
                .queryParam("role", authResponse.role())
                .build()
                .toUriString();

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
