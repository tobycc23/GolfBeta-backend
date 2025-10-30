package com.golfbeta.friend.dto;

import com.golfbeta.enums.FriendStatus;
import java.time.Instant;

public record FriendListItemDto(
        String otherUserId,
        String otherName,
        String otherUsername,
        FriendStatus status,
        boolean requestedByMe,
        Instant since
) {}
