package com.golfbeta.friend.request;

import com.golfbeta.friend.Friend;
import com.golfbeta.friend.FriendDomainHelper;
import com.golfbeta.friend.FriendRepository;
import com.golfbeta.friend.dto.FriendListItemDto;
import com.golfbeta.friend.dto.FriendViewDto;
import com.golfbeta.friend.enums.FriendStatus;
import com.golfbeta.notifications.PushNotificationService;
import com.golfbeta.notifications.NotificationInboxService;
import com.golfbeta.notifications.NotificationType;
import com.golfbeta.user.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final FriendRepository repo;
    private final FriendRequestAttemptRepository attempts;
    private final FriendDomainHelper helper;
    private final PushNotificationService pushNotifications;
    private final NotificationInboxService inbox;

    /**
     * Send a request. If the opposite pending request already exists, auto-accept to FRIENDS.
     * Idempotent if already requested by caller or already friends.
     */
    @Transactional
    public FriendViewDto request(String requesterId, String other) {
        helper.ensureNotSelf(requesterId, other);
        var requester = helper.requireProfile(requesterId);
        var otherProfile = helper.requireProfile(other);
        var pair = helper.canonical(requester.getId(), otherProfile.getId());

        var existing = repo.findByUserIdAAndUserIdB(pair.a(), pair.b());
        if (existing.isPresent()) {
            var f = existing.get();
            if (f.getStatus() == FriendStatus.FRIENDS) return helper.toView(requester, f);
            if (f.getRequesterId().equals(requester.getId())) return helper.toView(requester, f); // already requested by me
            // opposite pending -> accept
            f.setStatus(FriendStatus.FRIENDS);
            f.setUpdatedAt(Instant.now());
            recordAttempt(requester.getId(), otherProfile.getId());
            return helper.toView(requester, repo.save(f));
        }

        enforceRequestLimit(requester.getId(), otherProfile.getId());

        var f = new Friend();
        f.setUserIdA(pair.a());
        f.setUserIdB(pair.b());
        f.setRequesterId(requester.getId());
        f.setStatus(FriendStatus.REQUESTED);
        f.setCreatedAt(Instant.now());
        f.setUpdatedAt(Instant.now());

        try {
            var saved = repo.save(f);
            recordAttempt(requester.getId(), otherProfile.getId());
            trySendFriendRequestPush(requester, otherProfile);
            inbox.create(otherProfile.getId(), NotificationType.FRIEND_REQUEST, buildRequestMessage(requester), requester.getId());
            return helper.toView(requester, saved, helper.mapProfiles(requester, otherProfile));
        } catch (DataIntegrityViolationException race) {
            // If two requests happen at once, reload and re-evaluate
            var reloaded = repo.findByUserIdAAndUserIdB(pair.a(), pair.b())
                    .orElseThrow(() -> race);
            if (reloaded.getStatus() == FriendStatus.REQUESTED && !reloaded.getRequesterId().equals(requester.getId())) {
                reloaded.setStatus(FriendStatus.FRIENDS);
                reloaded.setUpdatedAt(Instant.now());
                recordAttempt(requester.getId(), otherProfile.getId());
                inbox.create(otherProfile.getId(), NotificationType.FRIEND_REQUEST, buildRequestMessage(requester), requester.getId());
                return helper.toView(requester, repo.save(reloaded));
            }
            recordAttempt(requester.getId(), otherProfile.getId());
            inbox.create(otherProfile.getId(), NotificationType.FRIEND_REQUEST, buildRequestMessage(requester), requester.getId());
            return helper.toView(requester, reloaded);
        }
    }

    /** Accept a received request. */
    public FriendViewDto accept(String uid, String other) {
        var viewer = helper.requireProfile(uid);
        var otherProfile = helper.requireProfile(other);
        var f = helper.loadPair(repo, viewer, otherProfile);
        if (f.getStatus() == FriendStatus.FRIENDS) return helper.toView(viewer, f);
        if (f.getRequesterId().equals(viewer.getId())) {
            throw new IllegalStateException("You cannot accept a request you sent");
        }
        f.setStatus(FriendStatus.FRIENDS);
        f.setUpdatedAt(Instant.now());
        inbox.create(otherProfile.getId(), NotificationType.FRIEND_REQUEST_ACCEPTED, buildAcceptedMessage(viewer), viewer.getId());
        return helper.toView(viewer, repo.save(f), helper.mapProfiles(viewer, otherProfile));
    }

    /** Reject a received request. Deletes the row. */
    public void reject(String uid, String other) {
        var viewer = helper.requireProfile(uid);
        var otherProfile = helper.requireProfile(other);
        var f = helper.loadPair(repo, viewer, otherProfile);
        if (f.getStatus() != FriendStatus.REQUESTED || f.getRequesterId().equals(viewer.getId())) {
            throw new IllegalStateException("No incoming request to reject");
        }
        repo.delete(f);
    }

    /** Cancel a request you sent. Deletes the row. */
    public void cancel(String uid, String other) {
        var viewer = helper.requireProfile(uid);
        var otherProfile = helper.requireProfile(other);
        var f = helper.loadPair(repo, viewer, otherProfile);
        if (f.getStatus() != FriendStatus.REQUESTED || !f.getRequesterId().equals(viewer.getId())) {
            throw new IllegalStateException("No outgoing request to cancel");
        }
        repo.delete(f);
    }

    /** Inbound requests only. */
    public List<FriendListItemDto> listIncoming(String uid) {
        var viewer = helper.requireProfile(uid);
        var rows = repo.findAllByUidAndStatus(viewer.getId(), FriendStatus.REQUESTED)
                .stream().filter(f -> !f.getRequesterId().equals(viewer.getId())).toList();
        return helper.enrichWithProfiles(viewer, rows);
    }

    /** Outbound requests only (useful for annotating search results on the client). */
    public List<FriendListItemDto> listOutgoing(String uid) {
        var viewer = helper.requireProfile(uid);
        var rows = repo.findAllByUidAndStatus(viewer.getId(), FriendStatus.REQUESTED)
                .stream().filter(f -> f.getRequesterId().equals(viewer.getId())).toList();
        return helper.enrichWithProfiles(viewer, rows);
    }

    private void enforceRequestLimit(java.util.UUID requesterId, java.util.UUID targetId) {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long recent = attempts.countRecentAttempts(requesterId, targetId, since);
        if (recent >= 2) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You can only send two friend requests to this user in a 30 day window");
        }
    }

    private void recordAttempt(java.util.UUID requesterId, java.util.UUID targetId) {
        var attempt = new FriendRequestAttempt();
        attempt.setRequesterId(requesterId);
        attempt.setTargetId(targetId);
        attempt.setAttemptedAt(Instant.now());
        attempts.save(attempt);
    }

    private void trySendFriendRequestPush(UserProfile requester, UserProfile recipient) {
        try {
            pushNotifications.sendFriendRequest(requester, recipient);
        } catch (Exception e) {
            // Do not block friend request on push failures.
            System.err.println("[FriendRequestService] Failed to send friend request push: " + e.getMessage());
        }
    }

    private String buildRequestMessage(UserProfile requester) {
        var name = requester.getName() != null ? requester.getName() : requester.getUsername();
        return "Friend request from " + (name != null ? name : "Someone");
    }

    private String buildAcceptedMessage(UserProfile accepter) {
        var name = accepter.getName() != null ? accepter.getName() : accepter.getUsername();
        return "Your friend request was accepted by " + (name != null ? name : "a user");
    }
}
