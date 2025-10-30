package com.golfbeta.friend;

import com.golfbeta.friend.dto.FriendListItemDto;
import com.golfbeta.friend.dto.FriendViewDto;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friends")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class FriendController {

    private final FriendService svc;

    public FriendController(FriendService svc) { this.svc = svc; }

    /** Relationship between me and another user, if any. */
    @GetMapping("/{otherUserId}")
    public FriendViewDto get(@AuthenticationPrincipal String uid,
                             @PathVariable("otherUserId") @NotBlank String otherUserId) {
        return svc.getRelationship(uid, otherUserId);
    }

    /** Send a request; auto-accepts if the other side has already requested me. */
    @PostMapping("/{otherUserId}")
    public FriendViewDto request(@AuthenticationPrincipal String uid,
                                 @PathVariable("otherUserId") @NotBlank String otherUserId) {
        return svc.request(uid, otherUserId);
    }

    /** Accept an incoming request. */
    @PostMapping("/{otherUserId}/accept")
    public FriendViewDto accept(@AuthenticationPrincipal String uid,
                                @PathVariable("otherUserId") @NotBlank String otherUserId) {
        return svc.accept(uid, otherUserId);
    }

    /** Reject an incoming request. */
    @PostMapping("/{otherUserId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal String uid,
                       @PathVariable("otherUserId") @NotBlank String otherUserId) {
        svc.reject(uid, otherUserId);
    }

    /** Cancel an outgoing request I sent. */
    @DeleteMapping("/{otherUserId}/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal String uid,
                       @PathVariable("otherUserId") @NotBlank String otherUserId) {
        svc.cancel(uid, otherUserId);
    }

    /** Unfriend an existing friend. */
    @DeleteMapping("/{otherUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@AuthenticationPrincipal String uid,
                         @PathVariable("otherUserId") @NotBlank String otherUserId) {
        svc.unfriend(uid, otherUserId);
    }

    /** Lists are split for simple client views. */
    @GetMapping
    public List<FriendListItemDto> listFriends(@AuthenticationPrincipal String uid) {
        return svc.listFriends(uid);
    }

    @GetMapping("/pending/incoming")
    public List<FriendListItemDto> listIncoming(@AuthenticationPrincipal String uid) {
        return svc.listIncoming(uid);
    }

    @GetMapping("/pending/outgoing")
    public List<FriendListItemDto> listOutgoing(@AuthenticationPrincipal String uid) {
        return svc.listOutgoing(uid);
    }
}
