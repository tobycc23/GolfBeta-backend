package com.golfbeta.video.asset.group.dto;

import jakarta.validation.constraints.NotBlank;

public record VideoGroupCreateRequestDto(
        @NotBlank String name
) {}
