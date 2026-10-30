package com.course_aura_hub.auth.mapper;

import com.course_aura_hub.auth.dto.AuthResponse;
import com.course_aura_hub.auth.dto.OAuth2UserDto;
import com.course_aura_hub.auth.dto.RegisterRequest;
import com.course_aura_hub.auth.dto.UserResponse;
import com.course_aura_hub.auth.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", constant = "ROLE_USER")
    @Mapping(target = "provider", constant = "LOCAL")
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "twitter", ignore = true)
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegisterRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", constant = "ROLE_USER")
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "twitter", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(OAuth2UserDto dto);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "provider", expression = "java(user.getProvider().name())")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "tokenType", constant = "Bearer")
    @Mapping(target = "expiresIn", source = "expiresIn")
    @Mapping(target = "sessionId", source = "sessionId")
    @Mapping(target = "refreshExpiresIn", source = "refreshExpiresIn")
    AuthResponse toAuthResponse(User user, String accessToken, String refreshToken, long expiresIn,
                                UUID sessionId, long refreshExpiresIn);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    @Mapping(target = "provider", expression = "java(user.getProvider().name())")
    UserResponse toUserResponse(User user);
}
