package com.golfbeta.converter;

import com.golfbeta.enums.ImprovementAreas;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Converter(autoApply = false)
public class ImprovementAreasConverter
        implements AttributeConverter<List<ImprovementAreas>, String[]> {

    @Override
    public String[] convertToDatabaseColumn(List<ImprovementAreas> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        return attribute.stream()
                .map(Enum::name)
                .toArray(String[]::new);
    }

    @Override
    public List<ImprovementAreas> convertToEntityAttribute(String[] dbData) {
        if (dbData == null || dbData.length == 0) return Collections.emptyList();
        return Arrays.stream(dbData)
                .map(ImprovementAreas::valueOf)
                .collect(Collectors.toList());
    }
}
