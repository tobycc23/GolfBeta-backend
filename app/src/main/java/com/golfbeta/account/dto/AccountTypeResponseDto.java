package com.golfbeta.account.dto;

import java.util.List;
import java.util.UUID;

public record AccountTypeResponseDto(
        String name,
        List<UUID> videoGroupIds
) {}
