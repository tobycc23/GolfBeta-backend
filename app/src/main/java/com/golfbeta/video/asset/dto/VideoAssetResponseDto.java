package com.golfbeta.video.asset.dto;

import java.time.Instant;
import java.util.UUID;

public record VideoAssetResponseDto(
        UUID id,
        String videoPath,
        Integer keyVersion,
        Instant createdAt,
        Instant updatedAt
) {}
