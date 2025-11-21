package com.golfbeta.user.dto;

import java.util.UUID;

public record VideoAssetSummaryDto(
        UUID id,
        String videoPath
) {}
