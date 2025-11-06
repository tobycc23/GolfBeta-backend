package com.golfbeta.user;

import com.golfbeta.user.dto.UserVideoResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.golfbeta.cdn.CloudFrontSignedUrlService;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserVideoService {

    private final UserSubscriptionRepository subscriptions;
    private final CloudFrontSignedUrlService cloudFrontSignedUrlService;

    @Value("${aws.cloudfront.signed-url-duration-seconds}")
    private long signedUrlDurationSeconds;

    public UserVideoResponseDto createPresignedUrls(String uid, String videoPath, VideoCodec codec) {
        if (videoPath == null || videoPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoPath is required");
        }

        boolean activeSubscription = subscriptions.findByUserProfileUserId(uid)
                .map(UserSubscription::isSubscribed)
                .orElse(false);

        if (!activeSubscription) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active subscription required");
        }

        String trimmedPath = videoPath.trim();
        if (trimmedPath.startsWith("/")) {
            trimmedPath = trimmedPath.substring(1);
        }
        if (trimmedPath.endsWith("/")) {
            trimmedPath = trimmedPath.substring(0, trimmedPath.length() - 1);
        }
        if (trimmedPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoPath is invalid");
        }

        String baseName = trimmedPath;
        int lastSlash = trimmedPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < trimmedPath.length() - 1) {
            baseName = trimmedPath.substring(lastSlash + 1);
        }

        Duration duration = Duration.ofSeconds(Math.max(1, signedUrlDurationSeconds));

        String prefix = "videos/" + trimmedPath;
        String videoKey = prefix + "/" + baseName + "_sourcefps_" + codec.value() + ".mp4";
        String metadataKey = prefix + "/" + baseName + "_metadata.json";

        UserVideoResponseDto userVideoResponseDto = new UserVideoResponseDto(
                cloudFrontSignedUrlService.generateSignedUrl(videoKey, duration),
                cloudFrontSignedUrlService.generateSignedUrl(metadataKey, duration),
                codec,
                duration.getSeconds()
        );

        return  userVideoResponseDto;
    }
}
