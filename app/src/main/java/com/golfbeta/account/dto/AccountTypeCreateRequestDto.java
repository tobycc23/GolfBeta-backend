package com.golfbeta.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountTypeCreateRequestDto(
        @NotBlank String name
) {}
