package com.golfbeta.friend;

import com.golfbeta.enums.FriendStatus;
import com.golfbeta.friend.dto.FriendListItemDto;
import com.golfbeta.friend.dto.FriendViewDto;
import com.golfbeta.user.UserProfile;
import com.golfbeta.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository repo;
    private final UserProfileRepository userProfiles;

    public FriendViewDto getRelationship(String uid, String other) {
        var viewer = requireProfile(uid);
        var otherProfile = requireProfile(other);
        var pair = canonical(viewer.getId(), otherProfile.getId());
        var f = repo.findByUserIdAAndUserIdB(pair.a(), pair.b())
                .orElseThrow(() -> new NoSuchElementException("No relationship found"));
        if (!f.involves(viewer.getId())) throw new NoSuchElementException("No relationship found");
        return toView(viewer, f, mapProfiles(viewer, otherProfile));
    }

    /**
     * Send a request. If the opposite pending request already exists, auto-accept to FRIENDS.
     * Idempotent if already requested by caller or already friends.
     */
    public FriendViewDto request(String requesterId, String other) {
        ensureNotSelf(requesterId, other);
        var requester = requireProfile(requesterId);
        var otherProfile = requireProfile(other);
        var pair = canonical(requester.getId(), otherProfile.getId());

        var existing = repo.findByUserIdAAndUserIdB(pair.a(), pair.b());
        if (existing.isPresent()) {
            var f = existing.get();
            if (f.getStatus() == FriendStatus.FRIENDS) return toView(requester, f);
            if (f.getRequesterId().equals(requester.getId())) return toView(requester, f); // already requested by me
            // opposite pending -> accept
            f.setStatus(FriendStatus.FRIENDS);
            f.setUpdatedAt(Instant.now());
            return toView(requester, repo.save(f));
        }

        var f = new Friend();
        f.setUserIdA(pair.a());
        f.setUserIdB(pair.b());
        f.setRequesterId(requester.getId());
        f.setStatus(FriendStatus.REQUESTED);
        f.setCreatedAt(Instant.now());
        f.setUpdatedAt(Instant.now());

        try {
            return toView(requester, repo.save(f), mapProfiles(requester, otherProfile));
        } catch (DataIntegrityViolationException race) {
            // If two requests happen at once, reload and re-evaluate
            var reloaded = repo.findByUserIdAAndUserIdB(pair.a(), pair.b())
                    .orElseThrow(() -> race);
            if (reloaded.getStatus() == FriendStatus.REQUESTED && !reloaded.getRequesterId().equals(requester.getId())) {
                reloaded.setStatus(FriendStatus.FRIENDS);
                reloaded.setUpdatedAt(Instant.now());
                return toView(requester, repo.save(reloaded));
            }
            return toView(requester, reloaded);
        }
    }

    /** Accept a received request. */
    public FriendViewDto accept(String uid, String other) {
        var viewer = requireProfile(uid);
        var otherProfile = requireProfile(other);
        var f = loadPair(viewer, otherProfile);
        if (f.getStatus() == FriendStatus.FRIENDS) return toView(viewer, f);
        if (f.getRequesterId().equals(viewer.getId())) {
            throw new IllegalStateException("You cannot accept a request you sent");
        }
        f.setStatus(FriendStatus.FRIENDS);
        f.setUpdatedAt(Instant.now());
        return toView(viewer, repo.save(f), mapProfiles(viewer, otherProfile));
    }

    /** Reject a received request. Deletes the row. */
    public void reject(String uid, String other) {
        var viewer = requireProfile(uid);
        var otherProfile = requireProfile(other);
        var f = loadPair(viewer, otherProfile);
        if (f.getStatus() != FriendStatus.REQUESTED || f.getRequesterId().equals(viewer.getId())) {
            throw new IllegalStateException("No incoming request to reject");
        }
        repo.delete(f);
    }

    /** Cancel a request you sent. Deletes the row. */
    public void cancel(String uid, String other) {
        var viewer = requireProfile(uid);
        var otherProfile = requireProfile(other);
        var f = loadPair(viewer, otherProfile);
        if (f.getStatus() != FriendStatus.REQUESTED || !f.getRequesterId().equals(viewer.getId())) {
            throw new IllegalStateException("No outgoing request to cancel");
        }
        repo.delete(f);
    }

    /** Unfriend (either side). Deletes the row. */
    public void unfriend(String uid, String other) {
        var viewer = requireProfile(uid);
        var otherProfile = requireProfile(other);
        var f = loadPair(viewer, otherProfile);
        if (f.getStatus() != FriendStatus.FRIENDS) {
            throw new IllegalStateException("Not friends");
        }
        repo.delete(f);
    }

    /** Friends only (no requests). */
    public List<FriendListItemDto> listFriends(String uid) {
        var viewer = requireProfile(uid);
        var rows = repo.findAllByUidAndStatus(viewer.getId(), FriendStatus.FRIENDS);
        return enrichWithProfiles(viewer, rows);
    }

    /** Inbound requests only. */
    public List<FriendListItemDto> listIncoming(String uid) {
        var viewer = requireProfile(uid);
        var rows = repo.findAllByUidAndStatus(viewer.getId(), FriendStatus.REQUESTED)
                .stream().filter(f -> !f.getRequesterId().equals(viewer.getId())).toList();
        return enrichWithProfiles(viewer, rows);
    }

    /** Outbound requests only (useful for annotating search results on the client). */
    public List<FriendListItemDto> listOutgoing(String uid) {
        var viewer = requireProfile(uid);
        var rows = repo.findAllByUidAndStatus(viewer.getId(), FriendStatus.REQUESTED)
                .stream().filter(f -> f.getRequesterId().equals(viewer.getId())).toList();
        return enrichWithProfiles(viewer, rows);
    }

    // ---- helpers

    private record Pair(UUID a, UUID b) {}
    private Pair canonical(UUID u1, UUID u2) {
        return (u1.compareTo(u2) < 0) ? new Pair(u1, u2) : new Pair(u2, u1);
    }

    private void ensureNotSelf(String u1, String u2) {
        if (u1.equals(u2)) throw new IllegalArgumentException("Cannot friend yourself");
    }

    private Friend loadPair(UserProfile viewer, UserProfile other) {
        var pair = canonical(viewer.getId(), other.getId());
        var f = repo.findByUserIdAAndUserIdB(pair.a(), pair.b())
                .orElseThrow(() -> new NoSuchElementException("No relationship found"));
        if (!f.involves(viewer.getId())) throw new NoSuchElementException("No relationship found");
        return f;
    }

    private FriendViewDto toView(UserProfile viewer, Friend f) {
        var profiles = userProfiles.findAllById(List.of(f.getUserIdA(), f.getUserIdB()))
                .stream()
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        profiles.putIfAbsent(viewer.getId(), viewer);
        return toView(viewer, f, profiles);
    }

    private FriendViewDto toView(UserProfile viewer, Friend f, Map<UUID, UserProfile> profiles) {
        profiles.putIfAbsent(viewer.getId(), viewer);
        UUID viewerId = viewer.getId();
        UUID otherId = f.otherOf(viewerId);
        UserProfile otherProfile = profiles.get(otherId);
        UserProfile userAProfile = profiles.get(f.getUserIdA());
        UserProfile userBProfile = profiles.get(f.getUserIdB());
        UserProfile requesterProfile = profiles.get(f.getRequesterId());

        String userIdA = userAProfile != null ? userAProfile.getFirebaseId() : f.getUserIdA().toString();
        String userIdB = userBProfile != null ? userBProfile.getFirebaseId() : f.getUserIdB().toString();
        String otherFirebaseId = otherProfile != null ? otherProfile.getFirebaseId() : otherId.toString();
        String requesterId = requesterProfile != null ? requesterProfile.getFirebaseId() : f.getRequesterId().toString();
        boolean requestedByMe = viewerId.equals(f.getRequesterId());

        return new FriendViewDto(
            f.getId(),
            userIdA,
            userIdB,
            otherFirebaseId,
            f.getStatus(),
            requesterId,
            requestedByMe,
            f.getCreatedAt(),
            f.getUpdatedAt()
        );
    }

    /**
     * Batch-enrich a list of Friend rows with other user's name/username,
     * avoiding N+1 queries.
     */
    private List<FriendListItemDto> enrichWithProfiles(UserProfile viewer, List<Friend> rows) {
        if (rows.isEmpty()) return List.of();

        UUID viewerId = viewer.getId();
        var otherIds = rows.stream()
                .map(f -> f.otherOf(viewerId))
                .collect(Collectors.toSet());

        Map<UUID, UserProfile> byId = userProfiles.findAllById(otherIds)
                .stream().collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        byId.put(viewerId, viewer);

        return rows.stream().map(f -> {
            UUID otherId = f.otherOf(viewerId);
            UserProfile p = byId.get(otherId);
            String otherFirebaseId = (p != null) ? p.getFirebaseId() : otherId.toString();
            String otherName = (p != null) ? p.getName() : null;
            String otherUsername = (p != null) ? p.getUsername() : null;

            return new FriendListItemDto(
                    otherFirebaseId,
                    otherName,
                    otherUsername,
                    f.getStatus(),
                    viewerId.equals(f.getRequesterId()),
                    f.getCreatedAt()
            );
        }).toList();
    }

    private UserProfile requireProfile(String firebaseId) {
        return userProfiles.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found"));
    }

    private Map<UUID, UserProfile> mapProfiles(UserProfile... profiles) {
        return Arrays.stream(profiles)
                .collect(Collectors.toMap(UserProfile::getId, Function.identity(), (existing, replacement) -> existing));
    }
}
