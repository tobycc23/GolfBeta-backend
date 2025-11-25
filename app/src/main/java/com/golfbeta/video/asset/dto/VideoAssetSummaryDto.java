package com.golfbeta.video.asset.dto;

import java.util.UUID;

public record VideoAssetSummaryDto(
        UUID id,
        String videoPath
) {}
