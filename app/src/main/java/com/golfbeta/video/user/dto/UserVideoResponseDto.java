package com.golfbeta.video.user.dto;

import com.golfbeta.video.VideoCodec;
import java.util.Map;

public record UserVideoResponseDto(
        String videoUrl,
        String metadataUrl,
        VideoCodec codec,
        long expiresInSeconds,
        Map<String, String> signedCookies
) {}
