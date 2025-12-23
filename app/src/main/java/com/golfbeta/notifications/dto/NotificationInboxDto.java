package com.golfbeta.notifications.dto;

import com.golfbeta.notifications.NotificationType;

import java.time.Instant;

public record NotificationInboxDto(
        Long id,
        NotificationType type,
        String message,
        Instant createdAt,
        boolean seen,
        String fromUserId
) {}
