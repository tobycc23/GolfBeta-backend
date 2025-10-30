package com.golfbeta.friend.dto;

import jakarta.validation.constraints.NotBlank;

/** Optional if you prefer path variables only; included for completeness. */
public record FriendActionRequestDto(
        @NotBlank String otherUserId
) {}