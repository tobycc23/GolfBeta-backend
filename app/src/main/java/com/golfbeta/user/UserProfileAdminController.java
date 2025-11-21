package com.golfbeta.user;

import com.golfbeta.admin.AdminAuditLogService;
import com.golfbeta.auth.AdminAuthorization;
import com.golfbeta.user.dto.UserProfileSummaryDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/user-profiles")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserProfileAdminController {

    private final UserProfileAdminService service;
    private final AdminAuthorization adminAuthorization;
    private final AdminAuditLogService auditLogService;

    @GetMapping("/search")
    public List<UserProfileSummaryDto> search(@AuthenticationPrincipal String uid,
                                              @RequestParam(name = "query", required = false) String query,
                                              @RequestParam(name = "limit", required = false) Integer limit) {
        adminAuthorization.assertAdmin(uid);
        return service.searchByName(query, limit);
    }
}
