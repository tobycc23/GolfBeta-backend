package com.golfbeta.admin.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    public void record(String adminUid, String action, String details) {
        if (!StringUtils.hasText(adminUid) || !StringUtils.hasText(action)) {
            return;
        }
        repository.save(new AdminAuditLog(adminUid, action, details));
    }
}
