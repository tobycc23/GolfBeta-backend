package com.golfbeta.friend.dto;

import com.golfbeta.enums.FriendStatus;

import java.time.Instant;

public record FriendViewDto(
        Long id,
        String userIdA,
        String userIdB,
        String otherUserId,
        FriendStatus status,
        String requesterId,
        boolean requestedByMe,
        Instant createdAt,
        Instant updatedAt
) {}