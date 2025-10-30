package com.golfbeta.practice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PracticeHundredStatusDto(
        UUID latestCompletedId,
        LocalDateTime latestCompletedAt
) {}
