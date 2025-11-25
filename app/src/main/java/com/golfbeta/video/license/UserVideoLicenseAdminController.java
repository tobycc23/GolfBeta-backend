package com.golfbeta.video.license;

import com.golfbeta.admin.audit.AdminAuditLogService;
import com.golfbeta.admin.AdminAuthorization;
import com.golfbeta.video.license.dto.VideoLicenseAdminRequestDto;
import com.golfbeta.video.license.dto.VideoLicenseAdminResponseDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/video-licenses")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RequiredArgsConstructor
public class UserVideoLicenseAdminController {

    private final AdminAuthorization adminAuthorization;
    private final UserVideoLicenseAdminService adminService;
    private final AdminAuditLogService auditLogService;

    @PostMapping
    public VideoLicenseAdminResponseDto upsert(@AuthenticationPrincipal String uid,
                                               @Valid @RequestBody VideoLicenseAdminRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        VideoLicenseAdminResponseDto response = adminService.upsert(request);
        auditLogService.record(uid, "VIDEO_LICENSE_UPSERT",
                "userId=%s,videoPath=%s,status=%s,expiresAt=%s".formatted(
                        request.userId(), request.videoPath(), request.status(), request.expiresAt()));
        return response;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String uid,
                       @RequestParam("userId") @NotBlank String userId,
                       @RequestParam("videoPath") @NotBlank String videoPath) {
        adminAuthorization.assertAdmin(uid);
        adminService.delete(userId, videoPath);
        auditLogService.record(uid, "VIDEO_LICENSE_DELETE",
                "userId=%s,videoPath=%s".formatted(userId, videoPath));
    }
}
