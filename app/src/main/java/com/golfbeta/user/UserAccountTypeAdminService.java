package com.golfbeta.user;

import com.golfbeta.user.dto.UserAccountTypeAssignmentDto;
import com.golfbeta.user.dto.UserAccountTypeUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserAccountTypeAdminService {

    private final UserAccountTypeRepository userAccountTypeRepository;
    private final UserProfileRepository userProfileRepository;
    private final AccountTypeRepository accountTypeRepository;

    @Transactional
    public UserAccountTypeAssignmentDto setAccountType(UserAccountTypeUpdateRequestDto request) {
        String userId = request.userId().trim();
        String desiredAccountType = normaliseAccountType(request.accountType());

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User profile not found: " + userId));

        AccountType accountType = accountTypeRepository.findById(desiredAccountType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account type not found: " + desiredAccountType));

        UserAccountType userAccountType = userAccountTypeRepository.findByUserProfileUserId(userId)
                .orElseGet(() -> {
                    UserAccountType entity = new UserAccountType();
                    entity.setUserProfile(profile);
                    return entity;
                });
        userAccountType.setAccountType(accountType);
        userAccountTypeRepository.save(userAccountType);
        return new UserAccountTypeAssignmentDto(userId, accountType.getName());
    }

    @Transactional(readOnly = true)
    public UserAccountTypeAssignmentDto findAccountType(String userId) {
        String trimmed = userId == null ? "" : userId.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }

        AccountType accountType = userAccountTypeRepository.findByUserProfileUserId(trimmed)
                .map(UserAccountType::getAccountType)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account type assignment for userId: " + trimmed));
        return new UserAccountTypeAssignmentDto(trimmed, accountType.getName());
    }

    private static String normaliseAccountType(String name) {
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountType is required");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountType is required");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
