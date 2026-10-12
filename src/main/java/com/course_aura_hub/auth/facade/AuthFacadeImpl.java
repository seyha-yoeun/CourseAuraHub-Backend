package com.course_aura_hub.auth.facade;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.LoginRequest;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.dto.RefreshTokenRequest;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.exception.AuthException;
import com.course_aura_hub.auth.service.AuthService;
import com.course_aura_hub.auth.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {

    private final AuthService authService;
    private final OAuth2Service oAuth2Service;

    @Override
    public AuthResponse register(RegisterRequest request) {
        log.info("Facade orchestrating registration for email: {}", request.email());
        return authService.register(request);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.info("Facade orchestrating login for email: {}", request.email());
        return authService.login(request);
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Facade orchestrating token refresh");
        return authService.refreshToken(request.refreshToken());
    }

    @Override
    public AuthResponse processOAuth2User(OAuth2UserDto oAuth2UserDto) {
        log.info("Facade orchestrating OAuth2 user provisioning for provider: {}", oAuth2UserDto.provider());
        return oAuth2Service.processOAuth2PostLogin(oAuth2UserDto);
    }

    @Override
    public AuthResponse processOAuth2Callback(String provider, String code, String state) {
        throw new AuthException("OAuth2 callback should be handled by Spring Security at /login/oauth2/code/{provider}. " +
                "Use GET /oauth2/authorization/{provider} to initiate the OAuth2 flow.");
    }

    @Override
    public void logout(String refreshToken) {
        log.info("Facade orchestrating logout");
        authService.logout(refreshToken);
    }
}
