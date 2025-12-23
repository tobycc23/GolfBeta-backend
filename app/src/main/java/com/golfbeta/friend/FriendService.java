package com.golfbeta.friend;

import com.golfbeta.friend.dto.FriendListItemDto;
import com.golfbeta.friend.dto.FriendViewDto;
import com.golfbeta.friend.enums.FriendStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository repo;
    private final FriendDomainHelper helper;

    public FriendViewDto getRelationship(String uid, String other) {
        var viewer = helper.requireProfile(uid);
        var otherProfile = helper.requireProfile(other);
        var f = helper.loadPair(repo, viewer, otherProfile);
        return helper.toView(viewer, f, helper.mapProfiles(viewer, otherProfile));
    }

    /** Unfriend (either side). Deletes the row. */
    public void unfriend(String uid, String other) {
        var viewer = helper.requireProfile(uid);
        var otherProfile = helper.requireProfile(other);
        var f = helper.loadPair(repo, viewer, otherProfile);
        if (f.getStatus() != FriendStatus.FRIENDS) {
            throw new IllegalStateException("Not friends");
        }
        repo.delete(f);
    }

    /** Friends only (no requests). */
    public List<FriendListItemDto> listFriends(String uid) {
        var viewer = helper.requireProfile(uid);
        var rows = repo.findAllByUidAndStatus(viewer.getId(), FriendStatus.FRIENDS);
        return helper.enrichWithProfiles(viewer, rows);
    }
}
