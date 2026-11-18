package com.course_aura_hub.notification.push;

import com.course_aura_hub.common.exception.BusinessRuleException;
import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushServiceImpl implements WebPushService {

    private static final int MAX_DEVICES = 10;
    private static final int MAX_USER_AGENT = 300;

    private final PushSubscriptionRepository subscriptionRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final WebPushProperties properties;

    @Override
    public PushDtos.VapidKeyResponse vapidKey() {
        return new PushDtos.VapidKeyResponse(properties.isConfigured() ? properties.vapidPublicKey().trim() : null,
                properties.isConfigured());
    }

    @Override
    @Transactional
    public PushDtos.SubscriptionStatus subscribe(UUID userId, PushDtos.SubscribeRequest request, String userAgent) {
        if (!properties.isConfigured()) {
            throw new ServiceUnavailableException("Browser notifications are not available");
        }
        String endpoint = request.endpoint().trim();
        requireAllowedEndpoint(endpoint);
        requireValidKeys(request.keys());

        PushSubscription subscription = subscriptionRepository.findByEndpoint(endpoint).orElse(null);
        if (subscription == null) {
            if (subscriptionRepository.countByUserId(userId) >= MAX_DEVICES) {
                throw new BusinessRuleException("You can turn on browser notifications on up to " + MAX_DEVICES + " devices");
            }
            subscription = PushSubscription.builder().endpoint(endpoint).build();
        }
        // A browser belongs to whoever signed in on it last
        subscription.setUserId(userId);
        subscription.setP256dh(request.keys().p256dh().trim());
        subscription.setAuth(request.keys().auth().trim());
        subscription.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT)));
        subscriptionRepository.save(subscription);
        return new PushDtos.SubscriptionStatus(true, subscriptionRepository.countByUserId(userId));
    }

    @Override
    @Transactional
    public PushDtos.SubscriptionStatus unsubscribe(UUID userId, String endpoint) {
        subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint.trim());
        long devices = subscriptionRepository.countByUserId(userId);
        return new PushDtos.SubscriptionStatus(devices > 0, devices);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PushDtos.Preference> preferences(UUID userId) {
        Set<String> muted = new HashSet<>(jdbc.queryForList(
                "select type from push_muted_types where user_id = :userId", new MapSqlParameterSource("userId", userId),
                String.class));
        return Arrays.stream(NotificationType.values())
                .map(type -> new PushDtos.Preference(type.apiValue(), !muted.contains(type.name())))
                .toList();
    }

    @Override
    @Transactional
    public List<PushDtos.Preference> updatePreferences(UUID userId, List<PushDtos.Preference> preferences) {
        for (PushDtos.Preference preference : preferences) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("type", parseType(preference.type()).name());
            if (preference.enabled()) {
                jdbc.update("delete from push_muted_types where user_id = :userId and type = :type", params);
            } else {
                jdbc.update("insert into push_muted_types (user_id, type) values (:userId, :type) on conflict do nothing", params);
            }
        }
        return preferences(userId);
    }

    /** The server posts to this URL, so only https push services on the allow-list are accepted. */
    private void requireAllowedEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("endpoint is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !properties.allowsHost(uri.getHost())) {
            throw new BusinessRuleException("endpoint must be a browser push service");
        }
    }

    private static void requireValidKeys(PushDtos.Keys keys) {
        try {
            WebPushCrypto.decodePublicKey(WebPushCrypto.decode(keys.p256dh()));
            if (WebPushCrypto.decode(keys.auth()).length != 16) {
                throw new IllegalArgumentException("auth must be 16 bytes");
            }
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new BusinessRuleException("The subscription keys are not valid");
        }
    }

    private static NotificationType parseType(String value) {
        try {
            return NotificationType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException("Unknown notification type: " + value);
        }
    }
}
