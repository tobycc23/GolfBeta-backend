package com.golfbeta.user.dto;

import com.golfbeta.user.license.VideoLicenseStatus;
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
