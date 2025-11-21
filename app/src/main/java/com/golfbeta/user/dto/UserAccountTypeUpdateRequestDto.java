package com.golfbeta.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserAccountTypeUpdateRequestDto(
        @NotBlank String userId,
        @NotBlank String accountType
) {}
