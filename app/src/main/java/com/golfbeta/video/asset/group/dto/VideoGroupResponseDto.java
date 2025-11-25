package com.golfbeta.video.asset.group.dto;

import java.util.List;
import java.util.UUID;

public record VideoGroupResponseDto(
        UUID id,
        String name,
        List<UUID> videoAssetIds
) {}
