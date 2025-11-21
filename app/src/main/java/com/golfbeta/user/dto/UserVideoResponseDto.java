package com.golfbeta.user.dto;

import com.golfbeta.user.VideoCodec;
import java.util.Map;

public record UserVideoResponseDto(
        String videoUrl,
        String metadataUrl,
        VideoCodec codec,
        long expiresInSeconds,
        Map<String, String> signedCookies
) {}
