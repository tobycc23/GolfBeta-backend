package com.golfbeta.user;

import com.golfbeta.user.dto.UserProfileSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileAdminService {

    private static final int DEFAULT_LIMIT = 20;

    private final UserProfileRepository repository;

    @Transactional(readOnly = true)
    public List<UserProfileSummaryDto> searchByName(String query, Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, 100));
        String sanitized = query == null ? "" : query.trim();
        List<Object[]> rows = repository.adminSearchByName(sanitized, effectiveLimit);
        return rows.stream()
                .map(row -> new UserProfileSummaryDto(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2]
                ))
                .toList();
    }
}
