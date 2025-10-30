package com.golfbeta.user;

import com.golfbeta.user.dto.UserProfilePatchDto;
import com.golfbeta.user.dto.UserProfilePutDto;
import com.golfbeta.user.dto.UserProfileViewDto;
import com.golfbeta.user.dto.UserSearchResultDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {
    private final UserProfileService svc;
    public UserProfileController(UserProfileService svc){ this.svc = svc; }

    @GetMapping("/me")
    public UserProfileViewDto me(@AuthenticationPrincipal String uid){
        return svc.getView(uid);
    }

    @PatchMapping("/profile")
    public UserProfileViewDto patch(@AuthenticationPrincipal String uid,
                                @RequestBody UserProfilePatchDto dto){
        return svc.patch(uid, dto);
    }

    @PutMapping("/profile")
    public UserProfileViewDto put(@AuthenticationPrincipal String uid,
                              @RequestBody @Validated UserProfilePutDto dto){
        return svc.put(uid, dto);
    }

    @DeleteMapping("/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal String uid){
        svc.deleteProfile(uid);
    }

    @GetMapping("/search")
    public List<UserSearchResultDto> search(
            @AuthenticationPrincipal String uid,
            @RequestParam("q") String q,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return svc.searchByName(uid, q, limit);
    }
}
