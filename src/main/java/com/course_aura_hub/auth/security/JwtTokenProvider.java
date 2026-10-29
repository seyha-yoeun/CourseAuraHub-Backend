package com.course_aura_hub.auth.security;

import com.course_aura_hub.auth.config.JwtProperties;
import com.course_aura_hub.auth.exception.TokenExpiredException;
import com.course_aura_hub.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";
    /** Session (refresh family) id; lets a single logout revoke the access tokens of that device. */
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String ACCESS_TOKEN_TYPE = "ACCESS";
    public static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // JwtProperties has already rejected missing, short and known-public secrets
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.signingKeyBytes());
    }

    public String generateAccessToken(User user, UUID sessionId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("firstName", user.getFirstName());
        extraClaims.put("lastName", user.getLastName());
        extraClaims.put("provider", user.getProvider().name());
        extraClaims.put(CLAIM_TOKEN_TYPE, ACCESS_TOKEN_TYPE);
        extraClaims.put(CLAIM_TOKEN_VERSION, user.getTokenVersion());
        extraClaims.put(CLAIM_SESSION_ID, sessionId.toString());

        return buildToken(extraClaims, user.getEmail(), jwtProperties.accessTokenExpiration());
    }

    public String generateRefreshToken(User user, UUID sessionId, long expirationMillis) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put(CLAIM_TOKEN_TYPE, REFRESH_TOKEN_TYPE);
        extraClaims.put(CLAIM_SESSION_ID, sessionId.toString());

        return buildToken(extraClaims, user.getEmail(), expirationMillis);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.debug("JWT token expired");
            throw new TokenExpiredException("JWT token has expired");
        } catch (SecurityException | MalformedJwtException ex) {
            log.warn("Invalid JWT signature/format");
            throw new IllegalArgumentException("Invalid JWT token");
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT");
            throw new IllegalArgumentException("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty");
            throw new IllegalArgumentException("JWT claims string is empty");
        }
    }

    public static UUID sessionIdOf(Claims claims) {
        Object raw = claims.get(CLAIM_SESSION_ID);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public long getAccessTokenExpiration() {
        return jwtProperties.accessTokenExpiration();
    }

    public long getRefreshTokenExpiration() {
        return jwtProperties.refreshTokenExpiration();
    }

    /** Refresh tokens are long random JWTs, so an unkeyed SHA-256 is sufficient (and matches stored legacy hashes). */
    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public boolean matchesRefreshToken(String rawToken, String hashedToken) {
        if (rawToken == null || hashedToken == null) {
            return false;
        }
        byte[] expectedHash = hashRefreshToken(rawToken).getBytes(StandardCharsets.UTF_8);
        byte[] storedHash = hashedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedHash, storedHash);
    }
}
