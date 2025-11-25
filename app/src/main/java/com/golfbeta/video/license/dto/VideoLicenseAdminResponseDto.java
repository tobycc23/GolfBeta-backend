package com.golfbeta.video.license.dto;

import com.golfbeta.video.license.VideoLicenseStatus;

import java.time.Instant;
import java.util.UUID;

public record VideoLicenseAdminResponseDto(
        UUID id,
        String userId,
        String videoPath,
        VideoLicenseStatus status,
        Instant expiresAt,
        Instant lastValidatedAt,
        Instant updatedAt
) {}
