package com.golfbeta.practice;

import com.golfbeta.practice.dto.PracticeHundredAnalysisResponseDto;
import com.golfbeta.practice.dto.PracticeHundredPatchDto;
import com.golfbeta.practice.dto.PracticeHundredResponseDto;
import com.golfbeta.practice.dto.PracticeHundredStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PracticeHundredService {

    private final PracticeHundredRepository repository;

    public PracticeHundredResponseDto create(String userId) {
        var practiceHundred = new PracticeHundred();
        practiceHundred.setId(UUID.randomUUID());
        practiceHundred.setUserId(userId);
        practiceHundred.setStartedAt(nowTruncatedToSeconds());

        return toDto(repository.save(practiceHundred));
    }

    public PracticeHundredResponseDto patch(String userId, UUID id, PracticeHundredPatchDto dto) {
        var practiceHundred = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice hundred not found"));

        applyPatch(practiceHundred, dto);

        return toDto(repository.save(practiceHundred));
    }

    public PracticeHundredResponseDto complete(String userId, UUID id, PracticeHundredPatchDto dto) {
        var practiceHundred = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice hundred not found"));

        if (dto != null) {
            applyPatch(practiceHundred, dto);
        }

        practiceHundred.setCompletedAt(nowTruncatedToSeconds());

        return toDto(repository.save(practiceHundred));
    }

    public PracticeHundredStatusDto latestCompleted(String userId) {
        return repository.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId)
                .map(ph -> new PracticeHundredStatusDto(ph.getId(), ph.getCompletedAt()))
                .orElse(new PracticeHundredStatusDto(null, null));
    }

    public PracticeHundredAnalysisResponseDto analysis(String userId) {
        var practiceHundred = repository.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No completed practice hundred found"));

        var drivingTotal = parseSingleScore(practiceHundred.getDrivingStraight())
                + parseSingleScore(practiceHundred.getDrivingDraw())
                + parseSingleScore(practiceHundred.getDrivingFade());
        var woodsTotal = parseSingleScore(practiceHundred.getWoodsStraight())
                + parseSingleScore(practiceHundred.getWoodsDraw())
                + parseSingleScore(practiceHundred.getWoodsFade());
        var longIronsTotal = parseSingleScore(practiceHundred.getLongironsStraight())
                + parseSingleScore(practiceHundred.getLongironsDraw())
                + parseSingleScore(practiceHundred.getLongironsFade());
        var shortIronsTotal = parseSingleScore(practiceHundred.getShortironsStraight())
                + parseSingleScore(practiceHundred.getShortironsDraw())
                + parseSingleScore(practiceHundred.getShortironsFade());

        var threeQuarterPitch = parseCounterScores(practiceHundred.getPitchingThreequarterpw());
        var fullPitch = parseCounterScores(practiceHundred.getPitchingFullpw());
        var highLobPitch = parseCounterScores(practiceHundred.getPitchingHighlobs());

        int pitchingPoints =
                (3 * getCounterValue(threeQuarterPitch, "inside25ft")) +
                (5 * getCounterValue(threeQuarterPitch, "inside10ft")) +
                (3 * getCounterValue(fullPitch, "inside25ft")) +
                (4 * getCounterValue(fullPitch, "inside10ft")) +
                (3 * getCounterValue(highLobPitch, "inside25ft")) +
                (4 * getCounterValue(highLobPitch, "inside10ft"));

        var chippingTen = parseCounterScores(practiceHundred.getChipping10yards());
        var chippingTwenty = parseCounterScores(practiceHundred.getChipping20yards());

        int chippingPoints =
                (4 * getCounterValue(chippingTen, "inside3ft")) +
                (5 * getCounterValue(chippingTen, "holed")) +
                (3 * getCounterValue(chippingTwenty, "inside3ft")) +
                (4 * getCounterValue(chippingTwenty, "holed"));

        int puttingPoints =
                (3 * parseSingleScore(practiceHundred.getPutting3ft())) +
                (2 * parseSingleScore(practiceHundred.getPutting6ft())) +
                (parseSingleScore(practiceHundred.getPutting15ft()));

        return new PracticeHundredAnalysisResponseDto(
                normalise(drivingTotal, 12),
                normalise(woodsTotal, 12),
                normalise(longIronsTotal, 12),
                normalise(shortIronsTotal, 12),
                normalise(pitchingPoints, 52),
                normalise(chippingPoints, 72),
                normalise(puttingPoints, 48)
        );
    }

    public List<PracticeHundredResponseDto> list(String userId) {
        return repository.findAllByUserIdOrderByStartedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<PracticeHundredResponseDto> history(String userId, int limit) {
        int sanitizedLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        Pageable pageable = PageRequest.of(0, sanitizedLimit, Sort.by(Sort.Direction.DESC, "completedAt"));
        return repository.findByUserIdAndCompletedAtIsNotNull(userId, pageable)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public PracticeHundredResponseDto findById(String userId, UUID id) {
        return repository.findByIdAndUserId(id, userId)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice hundred not found"));
    }

    public PracticeHundredResponseDto findIncomplete(String userId) {
        return repository.findFirstByUserIdAndCompletedAtIsNullOrderByStartedAtAsc(userId)
                .map(this::toDto)
                .orElse(null);
    }

    public void deleteIncomplete(String userId, UUID id) {
        var practiceHundred = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Practice hundred not found"));

        if (practiceHundred.getCompletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completed practice hundred cannot be deleted");
        }

        repository.delete(practiceHundred);
    }

    private void applyPatch(PracticeHundred practiceHundred, PracticeHundredPatchDto dto) {
        if (dto.putting3ft() != null) practiceHundred.setPutting3ft(dto.putting3ft());
        if (dto.putting6ft() != null) practiceHundred.setPutting6ft(dto.putting6ft());
        if (dto.putting15ft() != null) practiceHundred.setPutting15ft(dto.putting15ft());
        if (dto.chipping10yards() != null) practiceHundred.setChipping10yards(dto.chipping10yards());
        if (dto.chipping20yards() != null) practiceHundred.setChipping20yards(dto.chipping20yards());
        if (dto.pitchingFullpw() != null) practiceHundred.setPitchingFullpw(dto.pitchingFullpw());
        if (dto.pitchingThreequarterpw() != null) practiceHundred.setPitchingThreequarterpw(dto.pitchingThreequarterpw());
        if (dto.pitchingHighlobs() != null) practiceHundred.setPitchingHighlobs(dto.pitchingHighlobs());
        if (dto.shortironsStraight() != null) practiceHundred.setShortironsStraight(dto.shortironsStraight());
        if (dto.shortironsDraw() != null) practiceHundred.setShortironsDraw(dto.shortironsDraw());
        if (dto.shortironsFade() != null) practiceHundred.setShortironsFade(dto.shortironsFade());
        if (dto.longironsStraight() != null) practiceHundred.setLongironsStraight(dto.longironsStraight());
        if (dto.longironsDraw() != null) practiceHundred.setLongironsDraw(dto.longironsDraw());
        if (dto.longironsFade() != null) practiceHundred.setLongironsFade(dto.longironsFade());
        if (dto.woodsStraight() != null) practiceHundred.setWoodsStraight(dto.woodsStraight());
        if (dto.woodsDraw() != null) practiceHundred.setWoodsDraw(dto.woodsDraw());
        if (dto.woodsFade() != null) practiceHundred.setWoodsFade(dto.woodsFade());
        if (dto.drivingStraight() != null) practiceHundred.setDrivingStraight(dto.drivingStraight());
        if (dto.drivingDraw() != null) practiceHundred.setDrivingDraw(dto.drivingDraw());
        if (dto.drivingFade() != null) practiceHundred.setDrivingFade(dto.drivingFade());
    }

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+");

    private LocalDateTime nowTruncatedToSeconds() {
        return LocalDateTime.now().withNano(0);
    }

    private PracticeHundredResponseDto toDto(PracticeHundred practiceHundred) {
        return new PracticeHundredResponseDto(
                practiceHundred.getId(),
                practiceHundred.getUserId(),
                practiceHundred.getStartedAt(),
                practiceHundred.getCompletedAt(),
                practiceHundred.getPutting3ft(),
                practiceHundred.getPutting6ft(),
                practiceHundred.getPutting15ft(),
                practiceHundred.getChipping10yards(),
                practiceHundred.getChipping20yards(),
                practiceHundred.getPitchingFullpw(),
                practiceHundred.getPitchingThreequarterpw(),
                practiceHundred.getPitchingHighlobs(),
                practiceHundred.getShortironsStraight(),
                practiceHundred.getShortironsDraw(),
                practiceHundred.getShortironsFade(),
                practiceHundred.getLongironsStraight(),
                practiceHundred.getLongironsDraw(),
                practiceHundred.getLongironsFade(),
                practiceHundred.getWoodsStraight(),
                practiceHundred.getWoodsDraw(),
                practiceHundred.getWoodsFade(),
                practiceHundred.getDrivingStraight(),
                practiceHundred.getDrivingDraw(),
                practiceHundred.getDrivingFade()
        );
    }

    private int parseSingleScore(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        var matcher = NUMBER_PATTERN.matcher(value);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Map<String, Integer> parseCounterScores(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> scores = new HashMap<>();
        for (String part : value.split(",")) {
            var trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int delimiterIdx = trimmed.indexOf(':');
            if (delimiterIdx <= 0) {
                continue;
            }
            var numberPart = trimmed.substring(0, delimiterIdx).trim();
            var key = trimmed.substring(delimiterIdx + 1).trim().toLowerCase();
            if (key.isEmpty()) {
                continue;
            }
            var parsed = parseSingleScore(numberPart);
            scores.put(key, parsed);
        }
        return scores;
    }

    private int getCounterValue(Map<String, Integer> counters, String key) {
        return counters.getOrDefault(key.toLowerCase(), 0);
    }

    private double normalise(int value, int maximum) {
        if (maximum <= 0) {
            return 0.0;
        }
        var ratio = (double) value / maximum;
        if (ratio < 0.0) {
            return 0.0;
        }
        if (ratio > 1.0) {
            return 1.0;
        }
        return ratio;
    }
}
