package com.golfbeta.user.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UserProfilePutDto(
        @NotBlank String email,
        @NotBlank String name,
        @NotNull LocalDate dob,
        @Nullable @DecimalMax("54.0") @DecimalMin("-1.0") Double golfHandicap
) {}
