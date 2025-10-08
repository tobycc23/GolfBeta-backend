// src/main/java/com/golfbeta/user/dto/ProfileViewDto.java
package com.golfbeta.user.dto;

import com.golfbeta.enums.ImprovementAreas;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record UserProfileViewDto(
        String userId,
        String email,
        String name,
        LocalDate dob,
        String username,
        Double golfHandicap,
        Integer breakNumberTarget,
        String skillLevel,
        List<ImprovementAreas> improvementAreas,
        String favouriteColour,
        Instant createdAt,
        Instant updatedAt,
        Instant profileCompletedAt,
        int profileVersion,
        UserProfileStatusDto status
) {}
