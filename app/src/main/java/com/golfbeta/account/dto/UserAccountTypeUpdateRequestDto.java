package com.golfbeta.account.dto;

import jakarta.validation.constraints.NotBlank;

public record UserAccountTypeUpdateRequestDto(
        @NotBlank String userId,
        @NotBlank String accountType
) {}
