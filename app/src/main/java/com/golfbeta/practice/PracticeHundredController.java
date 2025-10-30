package com.golfbeta.practice;

import com.golfbeta.practice.dto.PracticeHundredAnalysisResponseDto;
import com.golfbeta.practice.dto.PracticeHundredPatchDto;
import com.golfbeta.practice.dto.PracticeHundredResponseDto;
import com.golfbeta.practice.dto.PracticeHundredStatusDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/practice-hundred")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class PracticeHundredController {

    private final PracticeHundredService service;

    public PracticeHundredController(PracticeHundredService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PracticeHundredResponseDto create(@AuthenticationPrincipal String uid) {
        return service.create(uid);
    }

    @GetMapping
    public List<PracticeHundredResponseDto> list(@AuthenticationPrincipal String uid) {
        return service.list(uid);
    }

    @GetMapping("/incomplete")
    public PracticeHundredResponseDto findIncomplete(@AuthenticationPrincipal String uid) {
        return service.findIncomplete(uid);
    }

    @GetMapping("/has-completed")
    public PracticeHundredStatusDto status(@AuthenticationPrincipal String uid) {
        return service.latestCompleted(uid);
    }

    @GetMapping("/analysis")
    public PracticeHundredAnalysisResponseDto analysis(@AuthenticationPrincipal String uid) {
        return service.analysis(uid);
    }

    @PatchMapping("/{id}")
    public PracticeHundredResponseDto patch(@AuthenticationPrincipal String uid,
                                            @PathVariable UUID id,
                                            @RequestBody @Valid PracticeHundredPatchDto dto) {
        return service.patch(uid, id, dto);
    }

    @PostMapping("/{id}/complete")
    public PracticeHundredResponseDto complete(@AuthenticationPrincipal String uid,
                                               @PathVariable UUID id,
                                               @RequestBody @Valid PracticeHundredPatchDto dto) {
        return service.complete(uid, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String uid, @PathVariable UUID id) {
        service.deleteIncomplete(uid, id);
    }
}
