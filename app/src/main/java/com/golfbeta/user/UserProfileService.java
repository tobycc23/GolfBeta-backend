package com.golfbeta.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.golfbeta.account.AccountType;
import com.golfbeta.account.AccountTypeRepository;
import com.golfbeta.account.UserAccountType;
import com.golfbeta.account.UserAccountTypeRepository;
import com.golfbeta.shared.enums.ImprovementAreas;
import com.golfbeta.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UsernameAllocator allocator;
    private final UserProfileRepository repo;
    private final UserAccountTypeRepository userAccountTypes;
    private final AccountTypeRepository accountTypeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public UserProfileViewDto getView(String uid) {
        var p = repo.findByFirebaseId(uid)
                .orElseGet(() -> repo.save(seed(uid, null)));
        ensureAccountType(p);
        var status = computeStatus(p);
        return toView(p, status);
    }

    public UserProfileViewDto patch(String uid, UserProfilePatchDto dto) {
        var p = repo.findByFirebaseId(uid).orElseGet(() -> seed(uid, null));

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
        ensureAccountType(p);

        status = computeStatus(p);
        return toView(p, status);
    }

    public UserProfileViewDto put(String uid, UserProfilePutDto dto) {
        var p = repo.findByFirebaseId(uid).orElseGet(() -> seed(uid, dto.email()));

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

        ensureAccountType(p);

        var status = computeStatus(p);
        return toView(p, status);
    }

    public void deleteProfile(String uid) {
        repo.findByFirebaseId(uid).ifPresent(repo::delete);
    }

    // ----- helpers -----
    private UserProfile seed(String uid, String email) {
        var np = new UserProfile();
        np.setFirebaseId(uid);
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
                p.getFirebaseId(), p.getEmail(), p.getName(), p.getDob(), p.getUsername(),
                p.getGolfHandicap(), p.getBreakNumberTarget(), p.getSkillLevel(),
                ImprovementAreas.filterNamesToEnums(p.getImprovementAreas()), p.getFavouriteColour(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getProfileCompletedAt(),
                Optional.ofNullable(p.getProfileVersion()).orElse(1),
                status
        );
    }

    private void ensureAccountType(UserProfile profile) {
        if (profile.getFirebaseId() == null) {
            return;
        }

        if (userAccountTypes.existsByUserProfileFirebaseId(profile.getFirebaseId())) {
            return;
        }

        var userAccountType = new UserAccountType();
        userAccountType.setUserProfile(profile);
        AccountType defaultAccountType = accountTypeRepository.findById("tier_1")
                .orElseGet(() -> accountTypeRepository.findById("tier_0")
                        .orElseThrow(() -> new IllegalStateException("Default account types not seeded")));
        userAccountType.setAccountType(defaultAccountType);
        userAccountTypes.save(userAccountType);
    }

    public List<UserSearchResultDto> searchByName(String uid, String q, Integer limit) {
        if (q == null || q.isBlank()) return List.of();
        int lim = (limit == null) ? 20 : Math.max(1, Math.min(limit, 50));
        var rows = repo.searchByNameFuzzy(q, uid, lim);
        return rows.stream()
                .map(r -> new UserSearchResultDto(
                        (String) r[0],
                        (String) r[1],
                        (String) r[2]
                ))
                .toList();
    }

}
