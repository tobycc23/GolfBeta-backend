package com.golfbeta.user.asset;

import com.golfbeta.admin.AdminAuditLogService;
import com.golfbeta.auth.AdminAuthorization;
import com.golfbeta.user.dto.VideoAssetRequestDto;
import com.golfbeta.user.dto.VideoAssetResponseDto;
import com.golfbeta.user.dto.VideoAssetSummaryDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/video-assets")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RequiredArgsConstructor
public class VideoAssetController {

    private final VideoAssetService service;
    private final AdminAuthorization adminAuthorization;
    private final AdminAuditLogService auditLogService;

    @PostMapping
    public VideoAssetResponseDto upsert(@AuthenticationPrincipal String uid,
                                        @Valid @RequestBody VideoAssetRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        VideoAssetResponseDto response = service.upsert(request);
        auditLogService.record(uid, "VIDEO_ASSET_UPSERT",
                "videoPath=%s,keyVersion=%s".formatted(request.videoPath(), request.keyVersion()));
        return response;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String uid,
                       @RequestParam("videoPath") @NotBlank String videoPath) {
        adminAuthorization.assertAdmin(uid);
        boolean removed = service.delete(videoPath);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video asset not found: " + videoPath);
        }
        auditLogService.record(uid, "VIDEO_ASSET_DELETE", "videoPath=%s".formatted(videoPath));
    }

    @GetMapping("/search")
    public java.util.List<VideoAssetSummaryDto> search(@AuthenticationPrincipal String uid,
                                                       @RequestParam(name = "query", required = false) String query) {
        adminAuthorization.assertAdmin(uid);
        return service.searchByVideoPath(query);
    }
}
