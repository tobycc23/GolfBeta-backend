package com.golfbeta.user.license;

import com.golfbeta.user.UserProfile;
import com.golfbeta.user.UserProfileRepository;
import com.golfbeta.user.VideoPathUtils;
import com.golfbeta.user.dto.VideoLicenseAdminRequestDto;
import com.golfbeta.user.dto.VideoLicenseAdminResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserVideoLicenseAdminService {

    private final UserVideoLicenseRepository licenseRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public VideoLicenseAdminResponseDto upsert(VideoLicenseAdminRequestDto request) {
        String userId = request.userId().trim();
        String normalisedVideoId = VideoPathUtils.normalise(request.videoPath());
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        UserVideoLicense license = licenseRepository.findByUserProfileUserIdAndVideoId(userId, normalisedVideoId)
                .orElseGet(() -> {
                    UserVideoLicense entity = new UserVideoLicense();
                    entity.setUserProfile(profile);
                    entity.setVideoId(normalisedVideoId);
                    return entity;
                });

        if (request.status() != null) {
            license.setStatus(request.status());
        }
        license.setExpiresAt(request.expiresAt());

        UserVideoLicense saved = licenseRepository.save(license);
        return mapResponse(saved);
    }

    @Transactional
    public void delete(String userId, String videoPath) {
        String normalisedVideoId = VideoPathUtils.normalise(videoPath);
        licenseRepository.findByUserProfileUserIdAndVideoId(userId.trim(), normalisedVideoId)
                .ifPresent(licenseRepository::delete);
    }

    private static VideoLicenseAdminResponseDto mapResponse(UserVideoLicense license) {
        return new VideoLicenseAdminResponseDto(
                license.getId(),
                license.getUserProfile().getUserId(),
                license.getVideoId(),
                license.getStatus(),
                license.getExpiresAt(),
                license.getLastValidatedAt(),
                license.getUpdatedAt()
        );
    }
}
