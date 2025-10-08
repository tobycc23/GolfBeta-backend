package com.golfbeta.user.dto;
import java.util.List;

public record UserProfileStatusDto(
        boolean completed,
        List<String> missingFields,
        int version
) {}
