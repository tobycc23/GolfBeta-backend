package com.golfbeta.video.license.dto;

import com.golfbeta.video.license.VideoLicenseDenialReason;
import com.golfbeta.video.license.VideoLicenseStatus;

import java.time.Instant;

public record VideoLicenseStatusResponseDto(
        String videoId,
        boolean licenseGranted,
        VideoLicenseStatus status,
        Instant expiresAt,
        Instant checkedAt,
        VideoLicenseDenialReason denialReason
) {}
