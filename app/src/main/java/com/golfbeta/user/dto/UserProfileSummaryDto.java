package com.golfbeta.user.dto;

public record UserProfileSummaryDto(
        String userId,
        String email,
        String name
) {}
