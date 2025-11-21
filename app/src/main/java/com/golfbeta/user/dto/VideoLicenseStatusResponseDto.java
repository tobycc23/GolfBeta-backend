package com.golfbeta.user.dto;

import com.golfbeta.user.license.VideoLicenseDenialReason;
import com.golfbeta.user.license.VideoLicenseStatus;

import java.time.Instant;

public record VideoLicenseStatusResponseDto(
        String videoId,
        boolean licenseGranted,
        VideoLicenseStatus status,
        Instant expiresAt,
        Instant checkedAt,
        VideoLicenseDenialReason denialReason
) {}
