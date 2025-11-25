package com.golfbeta.admin;

import com.golfbeta.admin.audit.AdminAuditLogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/session")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminSessionController {

    private final AdminAuthorization adminAuthorization;
    private final AdminAuditLogService adminAuditLogService;

    @GetMapping
    public AdminSessionResponse current(@AuthenticationPrincipal String uid,
                                        @RequestParam(value = "recordLogin", defaultValue = "false") boolean recordLogin) {
        adminAuthorization.assertAdmin(uid);
        if (recordLogin) {
            adminAuditLogService.record(uid, "ADMIN_SIGN_IN", "source=web_console");
        }
        return new AdminSessionResponse(uid);
    }

    public record AdminSessionResponse(String uid) {
    }
}
