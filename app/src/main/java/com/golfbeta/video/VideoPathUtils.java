package com.golfbeta.video;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

public final class VideoPathUtils {

    private VideoPathUtils() {
    }

    public static String normalise(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoPath is required");
        }

        String trimmed = rawPath.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        if (!StringUtils.hasText(trimmed)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoPath is invalid");
        }
        return trimmed;
    }
}
