package com.golfbeta.practice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PracticeHundredResponseDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("started_at") LocalDateTime startedAt,
        @JsonProperty("completed_at") LocalDateTime completedAt,
        @JsonProperty("putting_3ft") String putting3ft,
        @JsonProperty("putting_6ft") String putting6ft,
        @JsonProperty("putting_15ft") String putting15ft,
        @JsonProperty("chipping_10yards") String chipping10yards,
        @JsonProperty("chipping_20yards") String chipping20yards,
        @JsonProperty("pitching_fullpw") String pitchingFullpw,
        @JsonProperty("pitching_threequarterpw") String pitchingThreequarterpw,
        @JsonProperty("pitching_highlobs") String pitchingHighlobs,
        @JsonProperty("shortirons_straight") String shortironsStraight,
        @JsonProperty("shortirons_draw") String shortironsDraw,
        @JsonProperty("shortirons_fade") String shortironsFade,
        @JsonProperty("longirons_straight") String longironsStraight,
        @JsonProperty("longirons_draw") String longironsDraw,
        @JsonProperty("longirons_fade") String longironsFade,
        @JsonProperty("woods_straight") String woodsStraight,
        @JsonProperty("woods_draw") String woodsDraw,
        @JsonProperty("woods_fade") String woodsFade,
        @JsonProperty("driving_straight") String drivingStraight,
        @JsonProperty("driving_draw") String drivingDraw,
        @JsonProperty("driving_fade") String drivingFade
) { }
