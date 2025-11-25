package com.golfbeta.video.license;

import com.golfbeta.account.AccountType;
import com.golfbeta.account.UserAccountType;
import com.golfbeta.account.UserAccountTypeRepository;
import com.golfbeta.video.VideoPathUtils;
import com.golfbeta.video.asset.VideoAsset;
import com.golfbeta.video.asset.group.VideoAssetGroup;
import com.golfbeta.video.asset.group.VideoAssetGroupRepository;
import com.golfbeta.video.asset.VideoAssetRepository;
import com.golfbeta.video.license.dto.VideoLicenseStatusResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserVideoLicenseService {

    private final UserVideoLicenseRepository repository;
    private final UserAccountTypeRepository userAccountTypeRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final VideoAssetGroupRepository videoAssetGroupRepository;

    @Transactional
    public VideoLicenseStatusResponseDto checkLicenseStatus(String userId, String videoPath) {
        String normalisedVideoId = VideoPathUtils.normalise(videoPath);
        return evaluateLicense(userId, normalisedVideoId, true);
    }

    @Transactional
    public String ensureLicenseForPlayback(String userId, String videoPath) {
        String normalisedVideoId = VideoPathUtils.normalise(videoPath);
        VideoLicenseStatusResponseDto decision = evaluateLicense(userId, normalisedVideoId, true);
        if (!decision.licenseGranted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, denialMessage(decision));
        }
        return normalisedVideoId;
    }

    private VideoLicenseStatusResponseDto evaluateLicense(String userId,
                                                          String videoId,
                                                          boolean updateLastValidated) {
        Instant now = Instant.now();
        return repository.findByUserProfileFirebaseIdAndVideoId(userId, videoId)
                .map(license -> buildDecision(license, now, updateLastValidated))
                .orElseGet(() -> deriveAccountTypeDecision(userId, videoId, now));
    }

    private VideoLicenseStatusResponseDto buildDecision(UserVideoLicense license,
                                                        Instant now,
                                                        boolean updateLastValidated) {
        VideoLicenseStatus status = license.getStatus();
        VideoLicenseDenialReason denialReason = null;
        boolean granted = false;

        if (status == VideoLicenseStatus.ACTIVE) {
            boolean expired = license.getExpiresAt() != null && license.getExpiresAt().isBefore(now);
            if (!expired) {
                granted = true;
                if (updateLastValidated) {
                    license.setLastValidatedAt(now);
                }
            } else {
                denialReason = VideoLicenseDenialReason.LICENSE_EXPIRED;
            }
        } else if (status == VideoLicenseStatus.SUSPENDED) {
            denialReason = VideoLicenseDenialReason.LICENSE_SUSPENDED;
        } else if (status == VideoLicenseStatus.REVOKED) {
            denialReason = VideoLicenseDenialReason.LICENSE_REVOKED;
        }

        return new VideoLicenseStatusResponseDto(
                license.getVideoId(),
                granted,
                status,
                license.getExpiresAt(),
                now,
                denialReason
        );
    }

    private VideoLicenseStatusResponseDto deriveAccountTypeDecision(String userId,
                                                                    String videoId,
                                                                    Instant now) {
        boolean grantedByAccountType = accountTypeAllowsVideo(userId, videoId);
        if (grantedByAccountType) {
            return new VideoLicenseStatusResponseDto(
                    videoId,
                    true,
                    VideoLicenseStatus.ACTIVE,
                    null,
                    now,
                    null
            );
        }
        return new VideoLicenseStatusResponseDto(
                videoId,
                false,
                null,
                null,
                now,
                VideoLicenseDenialReason.LICENSE_NOT_FOUND
        );
    }

    private boolean accountTypeAllowsVideo(String userId, String videoId) {
        return userAccountTypeRepository.findByUserProfileFirebaseId(userId)
                .map(UserAccountType::getAccountType)
                .map(accountType -> doesAccountTypeAllowVideo(accountType, videoId))
                .orElse(false);
    }

    private boolean doesAccountTypeAllowVideo(AccountType accountType, String videoId) {
        List<UUID> groupIds = accountType.getVideoGroupIds();
        if (groupIds == null) {
            return true; // admin: all groups implicitly included
        }
        if (groupIds.isEmpty()) {
            return false;
        }

        return videoAssetRepository.findByVideoPath(videoId)
                .map(VideoAsset::getId)
                .map(assetId -> videoAssetGroupRepository.findAllById(groupIds).stream()
                        .map(VideoAssetGroup::getVideoAssetIds)
                        .filter(ids -> ids != null && !ids.isEmpty())
                        .anyMatch(ids -> ids.contains(assetId)))
                .orElse(false);
    }

    private static String denialMessage(VideoLicenseStatusResponseDto decision) {
        VideoLicenseDenialReason reason = decision.denialReason();
        if (reason == null) {
            return "License check failed";
        }
        return switch (reason) {
            case LICENSE_NOT_FOUND -> "No license exists for this video.";
            case LICENSE_SUSPENDED -> "This video license is suspended.";
            case LICENSE_REVOKED -> "This video license has been revoked.";
            case LICENSE_EXPIRED -> "This video license has expired.";
        };
    }
}
