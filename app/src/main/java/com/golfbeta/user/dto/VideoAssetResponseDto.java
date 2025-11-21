package com.golfbeta.user.dto;

import java.time.Instant;
import java.util.UUID;

public record VideoAssetResponseDto(
        UUID id,
        String videoPath,
        Integer keyVersion,
        Instant createdAt,
        Instant updatedAt
) {}
