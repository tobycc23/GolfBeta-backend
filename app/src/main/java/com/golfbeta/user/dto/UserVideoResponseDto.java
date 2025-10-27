package com.golfbeta.user.dto;

import com.golfbeta.user.VideoCodec;

public record UserVideoResponseDto(
        String videoUrl,
        String metadataUrl,
        VideoCodec codec,
        long expiresInSeconds
) {}
