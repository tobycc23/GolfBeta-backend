package com.golfbeta.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.golfbeta.enums.ImprovementAreas;
import com.golfbeta.user.dto.UserProfilePatchDto;
import com.golfbeta.user.dto.UserProfilePutDto;
import com.golfbeta.user.dto.UserProfileStatusDto;
import com.golfbeta.user.dto.UserProfileViewDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UsernameAllocator allocator;
    private final UserProfileRepository repo;
    private final UserSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public UserProfileViewDto getView(String uid) {
        var p = repo.findById(uid).orElseThrow(() -> new NoSuchElementException("Profile not found"));
        var status = computeStatus(p);
        return toView(p, status);
    }

    public UserProfileViewDto patch(String uid, UserProfilePatchDto dto) {
        var p = repo.findById(uid).orElseGet(() -> seed(uid, null));

        if(dto.name() != null) {
            p.setName(dto.name());
        }
        if(dto.dob() != null) {
            p.setDob(dto.dob());
        }
        if(dto.golfHandicap() != null) {
            p.setGolfHandicap(dto.golfHandicap());
        }
        if(dto.breakNumberTarget() != null) {
            p.setBreakNumberTarget(dto.breakNumberTarget());
        }
        if(dto.skillLevel() != null) {
            p.setSkillLevel(dto.skillLevel());
        }
        if(dto.improvementAreas() != null) {
            p.setImprovementAreas(ImprovementAreas.filterNames(dto.improvementAreas()));
        }

        // mark completed when all required are present
        var status = computeStatus(p);
        if (!status.completed() && p.getProfileCompletedAt() == null) {
            p.setProfileCompletedAt(Instant.now());
        }

        p.setUpdatedAt(Instant.now());
        // if it was completed before and user removed data, we may recompute completion
        p = repo.save(p);
        ensureSubscription(p);

        status = computeStatus(p);
        return toView(p, status);
    }

    public UserProfileViewDto put(String uid, UserProfilePutDto dto) {
        var p = repo.findById(uid).orElseGet(() -> seed(uid, dto.email()));

        p.setEmail(dto.email());
        p.setName(dto.name());
        p.setUsername(allocator.allocateUsername(dto.name()));
        p.setDob(dto.dob());
        p.setGolfHandicap(dto.golfHandicap());

        p.setUpdatedAt(Instant.now());

        try {
            p = repo.save(p);
        } catch (DataIntegrityViolationException dup) {
            // likely username uniqueness violation
            throw new UsernameConflictException("Username already taken");
        }

        ensureSubscription(p);

        var status = computeStatus(p);
        return toView(p, status);
    }

    public void deleteProfile(String uid) {
        // GDPR: hard delete (simplest). Alternatively soft-delete/anonymize.
        repo.deleteById(uid);
    }

    // ----- helpers -----
    private UserProfile seed(String uid, String email) {
        var np = new UserProfile();
        np.setUserId(uid);
        if(email != null) np.setEmail(email);
        np.setCreatedAt(Instant.now());
        np.setUpdatedAt(Instant.now());
        return np;
    }

    private UserProfileStatusDto computeStatus(UserProfile p) {
        var missing = new ArrayList<String>();
        if(nullOrBlank(p.getEmail())) missing.add("email");
        if(nullOrBlank(p.getName())) missing.add("name");
        if(p.getDob() == null) missing.add("dob");
        if(nullOrBlank(p.getUsername())) missing.add("username");
        if(nullOrBlank(p.getSkillLevel())) missing.add("skill_level");

        boolean completed = missing.isEmpty() && p.getProfileCompletedAt() != null;
        return new UserProfileStatusDto(completed, missing, Optional.ofNullable(p.getProfileVersion()).orElse(1));
    }

    private boolean nullOrBlank(String s) {
        return s == null || s.isBlank();
    }

    private UserProfileViewDto toView(UserProfile p, UserProfileStatusDto status) {
        return new UserProfileViewDto(
                p.getUserId(), p.getEmail(), p.getName(), p.getDob(), p.getUsername(),
                p.getGolfHandicap(), p.getBreakNumberTarget(), p.getSkillLevel(),
                ImprovementAreas.filterNamesToEnums(p.getImprovementAreas()), p.getFavouriteColour(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getProfileCompletedAt(),
                Optional.ofNullable(p.getProfileVersion()).orElse(1),
                status
        );
    }

    private void ensureSubscription(UserProfile profile) {
        if (profile.getUserId() == null) {
            return;
        }

        if (subscriptions.existsByUserProfileUserId(profile.getUserId())) {
            return;
        }

        var subscription = new UserSubscription();
        subscription.setUserProfile(profile);
        subscription.setSubscribed(true);
        subscriptions.save(subscription);
    }
}
