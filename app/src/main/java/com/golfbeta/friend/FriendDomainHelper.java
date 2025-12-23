package com.golfbeta.friend;

import com.golfbeta.friend.dto.FriendListItemDto;
import com.golfbeta.friend.dto.FriendViewDto;
import com.golfbeta.user.UserProfile;
import com.golfbeta.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FriendDomainHelper {

    private final UserProfileRepository userProfiles;

    public record Pair(UUID a, UUID b) {}

    public Pair canonical(UUID u1, UUID u2) {
        return (u1.toString().compareTo(u2.toString()) < 0) ? new Pair(u1, u2) : new Pair(u2, u1);
    }

    public void ensureNotSelf(String u1, String u2) {
        if (u1.equals(u2)) throw new IllegalArgumentException("Cannot friend yourself");
    }

    public UserProfile requireProfile(String firebaseId) {
        return userProfiles.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new NoSuchElementException("User profile not found"));
    }

    public Friend loadPair(FriendRepository repo, UserProfile viewer, UserProfile other) {
        var pair = canonical(viewer.getId(), other.getId());
        var f = repo.findByUserIdAAndUserIdB(pair.a(), pair.b())
                .orElseThrow(() -> new NoSuchElementException("No relationship found"));
        if (!f.involves(viewer.getId())) throw new NoSuchElementException("No relationship found");
        return f;
    }

    public FriendViewDto toView(UserProfile viewer, Friend f) {
        var profiles = userProfiles.findAllById(List.of(f.getUserIdA(), f.getUserIdB()))
                .stream()
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        profiles.putIfAbsent(viewer.getId(), viewer);
        return toView(viewer, f, profiles);
    }

    public FriendViewDto toView(UserProfile viewer, Friend f, Map<UUID, UserProfile> profiles) {
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

    public Map<UUID, UserProfile> mapProfiles(UserProfile... profiles) {
        return Arrays.stream(profiles)
                .collect(Collectors.toMap(UserProfile::getId, Function.identity(), (existing, replacement) -> existing));
    }

    /**
     * Batch-enrich a list of Friend rows with other user's name/username,
     * avoiding N+1 queries.
     */
    public List<FriendListItemDto> enrichWithProfiles(UserProfile viewer, List<Friend> rows) {
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
}
