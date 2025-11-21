package com.golfbeta.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VideoAssetRequestDto(
        @NotBlank(message = "videoPath is required")
        String videoPath,
        @NotBlank(message = "keyHex is required")
        @Size(min = 32, max = 32, message = "keyHex must be 32 hex characters")
        String keyHex,
        @NotBlank(message = "keyBase64 is required")
        String keyBase64,
        @Min(value = 1, message = "keyVersion must be >= 1")
        Integer keyVersion
) {}
