package com.course_aura_hub.auth.service;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.mapper.UserMapper;
import com.course_aura_hub.auth.model.AuthProvider;
import com.course_aura_hub.auth.model.User;
import com.course_aura_hub.auth.repository.UserRepository;
import com.course_aura_hub.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse processOAuth2PostLogin(OAuth2UserDto oAuth2UserDto) {
        String email = oAuth2UserDto.email().trim().toLowerCase();
        AuthProvider provider = oAuth2UserDto.provider();
        String providerId = oAuth2UserDto.providerId();

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            log.info("Existing user found for OAuth login: {}", email);
            // Link or update provider information if not already linked
            if (user.getProviderId() == null) {
                user.setProvider(provider);
                user.setProviderId(providerId);
            }
            if (oAuth2UserDto.imageUrl() != null && !oAuth2UserDto.imageUrl().isBlank()) {
                user.setImageUrl(oAuth2UserDto.imageUrl());
            }
            user = userRepository.save(user);
        } else {
            log.info("Creating new user from OAuth provider {}: {}", provider, email);
            user = userMapper.toEntity(oAuth2UserDto);
            user.setEmail(email);
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setImageUrl(oAuth2UserDto.imageUrl());
            user = userRepository.save(user);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        long expiresIn = jwtTokenProvider.getAccessTokenExpiration();

        user.setRefreshToken(jwtTokenProvider.hashRefreshToken(refreshToken));
        user.setTokensIssuedAt(Instant.now());
        user.setRefreshTokenExpiresAt(
                Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpiration())
        );
        userRepository.save(user);

        return userMapper.toAuthResponse(user, accessToken, refreshToken, expiresIn);
    }
}
