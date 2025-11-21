package com.golfbeta.user;

import com.golfbeta.admin.AdminAuditLogService;
import com.golfbeta.auth.AdminAuthorization;
import com.golfbeta.user.dto.UserAccountTypeAssignmentDto;
import com.golfbeta.user.dto.UserAccountTypeUpdateRequestDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/user-account-types")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserAccountTypeAdminController {

    private final UserAccountTypeAdminService service;
    private final AdminAuthorization adminAuthorization;
    private final AdminAuditLogService auditLogService;

    @PostMapping
    public UserAccountTypeAssignmentDto setAccountType(@AuthenticationPrincipal String uid,
                                                       @Valid @RequestBody UserAccountTypeUpdateRequestDto request) {
        adminAuthorization.assertAdmin(uid);
        UserAccountTypeAssignmentDto response = service.setAccountType(request);
        auditLogService.record(uid, "USER_ACCOUNT_TYPE_SET",
                "userId=%s,accountType=%s".formatted(response.userId(), response.accountType()));
        return response;
    }

    @GetMapping
    public UserAccountTypeAssignmentDto getAccountType(@AuthenticationPrincipal String uid,
                                                       @RequestParam("userId") String userId) {
        adminAuthorization.assertAdmin(uid);
        return service.findAccountType(userId);
    }
}
