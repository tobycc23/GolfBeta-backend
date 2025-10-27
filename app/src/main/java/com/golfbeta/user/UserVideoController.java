package com.golfbeta.user;

import com.golfbeta.user.dto.UserVideoResponseDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/video")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class UserVideoController {

    private final UserVideoService service;

    public UserVideoController(UserVideoService service) {
        this.service = service;
    }

    @GetMapping
    public UserVideoResponseDto getVideo(@AuthenticationPrincipal String uid,
                                         @RequestParam("videoPath") @NotBlank String videoPath,
                                         @RequestParam("codec") VideoCodec codec) {
        return service.createPresignedUrls(uid, videoPath, codec);
    }
}
