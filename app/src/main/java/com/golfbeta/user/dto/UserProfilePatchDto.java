package com.golfbeta.user.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

public record UserProfilePatchDto(
        @Nullable String name,
        @Nullable LocalDate dob,
        @Nullable @DecimalMax("54.0") @DecimalMin("0.0") Double golfHandicap,
        @Nullable Integer breakNumberTarget,
        @Nullable @Pattern(regexp="^(beginner|novice|intermediate|advanced|pro)$") String skillLevel,
        @Nullable List<String> improvementAreas,
        @Nullable String favouriteColour
) {}
