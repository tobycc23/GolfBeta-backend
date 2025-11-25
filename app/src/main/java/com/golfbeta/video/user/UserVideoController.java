package com.golfbeta.video.user;

import com.golfbeta.video.VideoCodec;
import com.golfbeta.video.asset.VideoAssetService;
import com.golfbeta.video.user.dto.UserVideoResponseDto;
import com.golfbeta.video.license.dto.VideoLicenseStatusResponseDto;
import com.golfbeta.video.license.UserVideoLicenseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/video")
@Validated
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserVideoController {

    private final UserVideoService service;
    private final UserVideoLicenseService licenseService;
    private final VideoAssetService assetService;

    @GetMapping
    public UserVideoResponseDto getVideo(@AuthenticationPrincipal String uid,
                                         @RequestParam("videoPath") @NotBlank String videoPath,
                                         @RequestParam("codec") VideoCodec codec) {
        return service.createPresignedUrls(uid, videoPath, codec);
    }

    @GetMapping("/license/status")
    public VideoLicenseStatusResponseDto checkLicense(@AuthenticationPrincipal String uid,
                                                      @RequestParam("videoPath") @NotBlank String videoPath) {
        return licenseService.checkLicenseStatus(uid, videoPath);
    }

    @GetMapping(value = "/license/key", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> fetchLicenseKey(@AuthenticationPrincipal String uid,
                                                  @RequestParam("videoPath") @NotBlank String videoPath,
                                                  @RequestParam(value = "codec", required = false) VideoCodec codec) {
        String normalisedPath = licenseService.ensureLicenseForPlayback(uid, videoPath);
        byte[] keyBytes = assetService.resolveKeyBytesOrThrow(normalisedPath);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(keyBytes);
    }
}
