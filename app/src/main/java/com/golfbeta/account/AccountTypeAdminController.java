package com.golfbeta.account;

import com.golfbeta.admin.audit.AdminAuditLogService;
import com.golfbeta.admin.AdminAuthorization;
import com.golfbeta.account.dto.AccountTypeCreateRequestDto;
import com.golfbeta.account.dto.AccountTypeResponseDto;
import com.golfbeta.account.dto.AccountTypeVideoGroupRequestDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/account-types")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AccountTypeAdminController {

    private final AccountTypeAdminService service;
    private final AdminAuthorization adminAuthorization;
    private final AdminAuditLogService auditLogService;

    @PostMapping
    public AccountTypeResponseDto createAccountType(@AuthenticationPrincipal String uid,
                                                    @Valid @RequestBody AccountTypeCreateRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        AccountTypeResponseDto response = service.createAccountType(request);
        auditLogService.record(uid, "ACCOUNT_TYPE_CREATE", "name=%s".formatted(response.name()));
        return response;
    }

    @PostMapping("/{name}/video-groups")
    public AccountTypeResponseDto addVideoGroup(@AuthenticationPrincipal String uid,
                                                @PathVariable String name,
                                                @Valid @RequestBody AccountTypeVideoGroupRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        AccountTypeResponseDto response = service.addVideoGroup(name, request.videoGroupId());
        auditLogService.record(uid, "ACCOUNT_TYPE_ADD_GROUP",
                "name=%s,groupId=%s".formatted(response.name(), request.videoGroupId()));
        return response;
    }

    @DeleteMapping("/{name}/video-groups/{videoGroupId}")
    public AccountTypeResponseDto removeVideoGroup(@AuthenticationPrincipal String uid,
                                                   @PathVariable String name,
                                                   @PathVariable UUID videoGroupId) {
        adminAuthorization.assertAdmin(uid);
        AccountTypeResponseDto response = service.removeVideoGroup(name, videoGroupId);
        auditLogService.record(uid, "ACCOUNT_TYPE_REMOVE_GROUP",
                "name=%s,groupId=%s".formatted(response.name(), videoGroupId));
        return response;
    }

    @GetMapping
    public List<AccountTypeResponseDto> listAccountTypes(@AuthenticationPrincipal String uid) {
        adminAuthorization.assertAdmin(uid);
        return service.listAccountTypes();
    }
}
