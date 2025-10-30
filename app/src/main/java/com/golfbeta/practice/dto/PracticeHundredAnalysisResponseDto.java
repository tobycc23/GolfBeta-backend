package com.golfbeta.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PracticeHundredAnalysisResponseDto(
        @JsonProperty("driving") double driving,
        @JsonProperty("woods") double woods,
        @JsonProperty("longirons") double longirons,
        @JsonProperty("shortirons") double shortirons,
        @JsonProperty("pitching") double pitching,
        @JsonProperty("chipping") double chipping,
        @JsonProperty("putting") double putting
) { }

