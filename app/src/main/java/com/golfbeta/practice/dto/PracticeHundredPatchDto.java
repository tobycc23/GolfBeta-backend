package com.golfbeta.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

public record PracticeHundredPatchDto(
        @Nullable @JsonProperty("putting_3ft") String putting3ft,
        @Nullable @JsonProperty("putting_6ft") String putting6ft,
        @Nullable @JsonProperty("putting_15ft") String putting15ft,
        @Nullable @JsonProperty("chipping_10yards") String chipping10yards,
        @Nullable @JsonProperty("chipping_20yards") String chipping20yards,
        @Nullable @JsonProperty("pitching_fullpw") String pitchingFullpw,
        @Nullable @JsonProperty("pitching_threequarterpw") String pitchingThreequarterpw,
        @Nullable @JsonProperty("pitching_highlobs") String pitchingHighlobs,
        @Nullable @JsonProperty("shortirons_straight") String shortironsStraight,
        @Nullable @JsonProperty("shortirons_draw") String shortironsDraw,
        @Nullable @JsonProperty("shortirons_fade") String shortironsFade,
        @Nullable @JsonProperty("longirons_straight") String longironsStraight,
        @Nullable @JsonProperty("longirons_draw") String longironsDraw,
        @Nullable @JsonProperty("longirons_fade") String longironsFade,
        @Nullable @JsonProperty("woods_straight") String woodsStraight,
        @Nullable @JsonProperty("woods_draw") String woodsDraw,
        @Nullable @JsonProperty("woods_fade") String woodsFade,
        @Nullable @JsonProperty("driving_straight") String drivingStraight,
        @Nullable @JsonProperty("driving_draw") String drivingDraw,
        @Nullable @JsonProperty("driving_fade") String drivingFade
) { }
