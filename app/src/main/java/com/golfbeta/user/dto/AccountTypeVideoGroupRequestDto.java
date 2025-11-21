package com.golfbeta.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AccountTypeVideoGroupRequestDto(
        @NotNull UUID videoGroupId
) {}
