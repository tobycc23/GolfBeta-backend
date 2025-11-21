package com.golfbeta.user;

import com.golfbeta.cdn.CloudFrontSignedUrlService;
import com.golfbeta.user.dto.UserVideoResponseDto;
import com.golfbeta.user.license.UserVideoLicenseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UserVideoService {

    private static final Logger log = LoggerFactory.getLogger(UserVideoService.class);

    private final UserVideoLicenseService licenseService;
    private final CloudFrontSignedUrlService cloudFrontSignedUrlService;

    @Value("${aws.cloudfront.signed-url-duration-seconds}")
    private long signedUrlDurationSeconds;

    public UserVideoResponseDto createPresignedUrls(String uid, String videoPath, VideoCodec codec) {
        String normalisedPath = licenseService.ensureLicenseForPlayback(uid, videoPath);

        String baseName = normalisedPath;
        int lastSlash = normalisedPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < normalisedPath.length() - 1) {
            baseName = normalisedPath.substring(lastSlash + 1);
        }

        Duration duration = Duration.ofSeconds(Math.max(1, signedUrlDurationSeconds));

        String prefix = "videos/" + normalisedPath;
        String videoKey = prefix + "/" + baseName + "_sourcefps_" + codec.value() + ".mp4";
        String metadataKey = prefix + "/" + baseName + "_metadata.json";

        String videoSignedUrl = cloudFrontSignedUrlService.generateSignedUrl(videoKey, duration);
        String metadataSignedUrl = cloudFrontSignedUrlService.generateSignedUrl(metadataKey, duration);
        String cookieResourcePrefix = "videos/" + normalisedPath + "/";
        UserVideoResponseDto userVideoResponseDto = new UserVideoResponseDto(
                videoSignedUrl,
                metadataSignedUrl,
                codec,
                duration.getSeconds(),
                cloudFrontSignedUrlService.generateSignedCookies(cookieResourcePrefix, duration)
        );
        log.info("Generated signed URLs for uid={} videoPath={} codec={} videoKey={} metadataKey={} ttl={}s",
                uid, normalisedPath, codec, videoKey, metadataKey, duration.getSeconds());

        return userVideoResponseDto;
    }
}
