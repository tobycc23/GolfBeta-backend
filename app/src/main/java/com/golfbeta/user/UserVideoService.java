package com.golfbeta.user;

import com.golfbeta.user.dto.UserVideoResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserVideoService {

    private final UserSubscriptionRepository subscriptions;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.presign-duration-seconds}")
    private long presignDurationSeconds;

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

        if (bucket == null || bucket.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 bucket is not configured");
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

        Duration duration = Duration.ofSeconds(Math.max(1, presignDurationSeconds));

        String prefix = "videos/" + trimmedPath;
        String videoKey = prefix + "/" + baseName + "_sourcefps_" + codec.value() + ".mp4";
        String metadataKey = prefix + "/" + baseName + "_metadata.json";

        UserVideoResponseDto userVideoResponseDto = new UserVideoResponseDto(
                presign(videoKey, duration),
                presign(metadataKey, duration),
                codec,
                duration.getSeconds()
        );

        return  userVideoResponseDto;
    }

    private String presign(String key, Duration duration) {
        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(req -> req.bucket(bucket).key(key))
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
