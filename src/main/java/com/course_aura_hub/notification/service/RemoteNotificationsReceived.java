package com.course_aura_hub.notification.service;

import java.util.List;

/** Notifications another instance saved, to push to streams open on this one. */
public record RemoteNotificationsReceived(List<Long> notificationIds) {
}
