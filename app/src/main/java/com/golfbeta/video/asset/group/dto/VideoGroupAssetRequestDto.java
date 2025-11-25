package com.golfbeta.video.asset.group.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VideoGroupAssetRequestDto(
        @NotNull UUID videoAssetId
) {}
