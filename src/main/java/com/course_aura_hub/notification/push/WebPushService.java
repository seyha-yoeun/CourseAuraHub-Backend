package com.course_aura_hub.notification.push;

import java.util.List;
import java.util.UUID;

/** Browser push subscriptions and which notification types are pushed. */
public interface WebPushService {

    PushDtos.VapidKeyResponse vapidKey();

    /** Saves or takes over a browser's subscription for this user. Only known push services are accepted. */
    PushDtos.SubscriptionStatus subscribe(UUID userId, PushDtos.SubscribeRequest request, String userAgent);

    PushDtos.SubscriptionStatus unsubscribe(UUID userId, String endpoint);

    /** Every notification type, pushed unless the user muted it. */
    List<PushDtos.Preference> preferences(UUID userId);

    /** Only the listed types change. */
    List<PushDtos.Preference> updatePreferences(UUID userId, List<PushDtos.Preference> preferences);
}
