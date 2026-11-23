package com.foodrescue.dto;

import java.time.Instant;

import com.foodrescue.entity.Notification;
import com.foodrescue.enums.NotificationType;

public record NotificationResponse(
    Long id,
    NotificationType type,
    String title,
    String message,
    Long donationId,
    boolean read,
    Instant createdAt,
    Instant readAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getDonationId(),
            notification.isRead(),
            notification.getCreatedAt(),
            notification.getReadAt()
        );
    }
}
