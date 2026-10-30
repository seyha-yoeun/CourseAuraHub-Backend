package com.course_aura_hub.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String GENERIC_ERROR_CODE = "oauth2_failed";

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/redirect}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {

        String providerError = exception instanceof OAuth2AuthenticationException oauthException
                ? oauthException.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("OAuth2 authentication failure: {}", providerError);

        // Only a fixed code reaches the browser; provider messages can contain internal details
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", GENERIC_ERROR_CODE)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
