package com.golfbeta.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountTypeCreateRequestDto(
        @NotBlank String name
) {}
