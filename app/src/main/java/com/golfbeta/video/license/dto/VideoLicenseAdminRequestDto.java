package com.golfbeta.video.license.dto;

import com.golfbeta.video.license.VideoLicenseStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record VideoLicenseAdminRequestDto(
        @NotBlank(message = "userId is required")
        String userId,
        @NotBlank(message = "videoPath is required")
        String videoPath,
        VideoLicenseStatus status,
        Instant expiresAt
) {}
