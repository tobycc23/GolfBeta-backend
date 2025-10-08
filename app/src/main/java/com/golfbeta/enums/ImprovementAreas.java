package com.golfbeta.enums;


import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
public enum ImprovementAreas {
    DRIVING("Driving"),
    LONG_IRONS("Long Irons"),
    SHORT_IRONS("Short Irons"),
    GREENS_IN_REGULATION(  "Greens in Regulation"),
    PITCHING("Pitching"),
    CHIPPING("Chipping"),
    PUTTING("Putting"),
    SHORT_GAME("Short Game"),
    BUNKER_PLAY("Bunker Play"),
    COURSE_MANAGEMENT("Course Management");

    private final String name;

    ImprovementAreas(String name) {
        this.name = name;
    }

    public static List<ImprovementAreas> filterNamesToEnums(List<String> names) {
        if (names == null) {
            return List.of();
        }

        return names.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(name -> Arrays.stream(values())
                        .filter(area -> area.getName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    //Names to ImprovementAreas and back to Names, filtering out on the way. This is to store Names in db
    public static List<String> filterNames(List<String> names) {
        return filterNamesToEnums(names).stream()
                .map(ImprovementAreas::getName)
                .toList();
    }
}
