package com.golfbeta.account.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AccountTypeVideoGroupRequestDto(
        @NotNull UUID videoGroupId
) {}
