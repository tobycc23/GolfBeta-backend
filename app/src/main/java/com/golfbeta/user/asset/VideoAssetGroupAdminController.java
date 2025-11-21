package com.golfbeta.user.asset;

import com.golfbeta.admin.AdminAuditLogService;
import com.golfbeta.auth.AdminAuthorization;
import com.golfbeta.user.dto.VideoGroupAssetRequestDto;
import com.golfbeta.user.dto.VideoGroupCreateRequestDto;
import com.golfbeta.user.dto.VideoGroupResponseDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/video-groups")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VideoAssetGroupAdminController {

    private final VideoAssetGroupAdminService service;
    private final AdminAuthorization adminAuthorization;
    private final AdminAuditLogService auditLogService;

    @PostMapping
    public VideoGroupResponseDto createGroup(@AuthenticationPrincipal String uid,
                                             @Valid @RequestBody VideoGroupCreateRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        VideoGroupResponseDto response = service.createVideoGroup(request);
        auditLogService.record(uid, "VIDEO_GROUP_CREATE", "name=%s".formatted(response.name()));
        return response;
    }

    @PostMapping("/{groupId}/assets")
    public VideoGroupResponseDto addVideoAsset(@AuthenticationPrincipal String uid,
                                               @PathVariable UUID groupId,
                                               @Valid @RequestBody VideoGroupAssetRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        VideoGroupResponseDto response = service.addVideoAsset(groupId, request);
        auditLogService.record(uid, "VIDEO_GROUP_ADD_ASSET",
                "groupId=%s,assetId=%s".formatted(groupId, request.videoAssetId()));
        return response;
    }

    @DeleteMapping("/{groupId}/assets/{videoAssetId}")
    public VideoGroupResponseDto removeVideoAsset(@AuthenticationPrincipal String uid,
                                                  @PathVariable UUID groupId,
                                                  @PathVariable UUID videoAssetId) {
        adminAuthorization.assertAdmin(uid);
        VideoGroupResponseDto response = service.removeVideoAsset(groupId, videoAssetId);
        auditLogService.record(uid, "VIDEO_GROUP_REMOVE_ASSET",
                "groupId=%s,assetId=%s".formatted(groupId, videoAssetId));
        return response;
    }

    @GetMapping("/search")
    public List<VideoGroupResponseDto> search(@AuthenticationPrincipal String uid,
                                              @RequestParam(name = "query", required = false) String query) {
        adminAuthorization.assertAdmin(uid);
        return service.searchByName(query);
    }
}
