package com.golfbeta.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminAuthorization {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthorization.class);

    private final Set<String> adminUids;

    public AdminAuthorization(@Value("${security.admin-uids:}") String adminUidsRaw) {
        if (!StringUtils.hasText(adminUidsRaw)) {
            this.adminUids = Collections.emptySet();
            log.warn("No admin UIDs configured (security.admin-uids). Admin endpoints will reject all requests.");
        } else {
            this.adminUids = Arrays.stream(adminUidsRaw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toUnmodifiableSet());
            log.info("Configured {} admin UID(s) for protected endpoints", this.adminUids.size());
        }
    }

    public void assertAdmin(String uid) {
        if (!StringUtils.hasText(uid) || !adminUids.contains(uid)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges required");
        }
    }
}
