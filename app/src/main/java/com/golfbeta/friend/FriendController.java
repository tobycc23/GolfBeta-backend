package com.golfbeta.friend;

import com.golfbeta.friend.dto.FriendListItemDto;
import com.golfbeta.friend.dto.FriendViewDto;
import com.golfbeta.friend.request.FriendRequestService;
import com.golfbeta.friend.request.FriendRequestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friends")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class FriendController {

    private static final Logger log = LoggerFactory.getLogger(FriendController.class);
    private final FriendService friendService;
    private final FriendRequestService requestService;

    public FriendController(FriendService friendService, FriendRequestService requestService) {
        this.friendService = friendService;
        this.requestService = requestService;
    }

    /** Relationship between me and another user, if any. */
    @GetMapping("/{otherUserId}")
    public FriendViewDto get(@AuthenticationPrincipal String uid,
                             @PathVariable("otherUserId") @NotBlank String otherUserId) {
        return friendService.getRelationship(uid, otherUserId);
    }

    /** Send a request; auto-accepts if the other side has already requested me. */
    @PostMapping("/{otherUserId}")
    public FriendViewDto request(@AuthenticationPrincipal String uid,
                                 @PathVariable("otherUserId") @NotBlank String otherUserId) {
        return requestService.request(uid, otherUserId);
    }

    /** Accept an incoming request. */
    @PostMapping("/{otherUserId}/accept")
    public ResponseEntity<FriendViewDto> accept(@AuthenticationPrincipal String uid,
                                                @PathVariable("otherUserId") @NotBlank String otherUserId) {
        try {
            return ResponseEntity.ok(requestService.accept(uid, otherUserId));
        } catch (IllegalStateException | java.util.NoSuchElementException ex) {
            // Request was already removed; log as warning and respond with no content to avoid client errors.
            log.warn("Accept on missing/invalid request from {} -> {}: {}", uid, otherUserId, ex.getMessage());
            return ResponseEntity.noContent().build();
        }
    }

    /** Reject an incoming request. */
    @PostMapping("/{otherUserId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal String uid,
                       @PathVariable("otherUserId") @NotBlank String otherUserId) {
        try {
          requestService.reject(uid, otherUserId);
        } catch (IllegalStateException | java.util.NoSuchElementException ex) {
          log.warn("Reject on missing/invalid request from {} -> {}: {}", uid, otherUserId, ex.getMessage());
        }
    }

    /** Cancel an outgoing request I sent. */
    @DeleteMapping("/{otherUserId}/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal String uid,
                       @PathVariable("otherUserId") @NotBlank String otherUserId) {
        try {
          requestService.cancel(uid, otherUserId);
        } catch (IllegalStateException | java.util.NoSuchElementException ex) {
          log.warn("Cancel on missing/invalid request from {} -> {}: {}", uid, otherUserId, ex.getMessage());
        }
    }

    /** Unfriend an existing friend. */
    @DeleteMapping("/{otherUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@AuthenticationPrincipal String uid,
                         @PathVariable("otherUserId") @NotBlank String otherUserId) {
        try {
          friendService.unfriend(uid, otherUserId);
        } catch (IllegalStateException | java.util.NoSuchElementException ex) {
          log.warn("Unfriend on missing/invalid request from {} -> {}: {}", uid, otherUserId, ex.getMessage());
        }
    }

    /** Lists are split for simple client views. */
    @GetMapping
    public List<FriendListItemDto> listFriends(@AuthenticationPrincipal String uid) {
        return friendService.listFriends(uid);
    }

    @GetMapping("/pending/incoming")
    public List<FriendListItemDto> listIncoming(@AuthenticationPrincipal String uid) {
        return requestService.listIncoming(uid);
    }

    @GetMapping("/pending/outgoing")
    public List<FriendListItemDto> listOutgoing(@AuthenticationPrincipal String uid) {
        return requestService.listOutgoing(uid);
    }
}
