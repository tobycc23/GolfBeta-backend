package com.golfbeta.user;

import com.golfbeta.user.asset.VideoAssetGroupRepository;
import com.golfbeta.user.dto.AccountTypeCreateRequestDto;
import com.golfbeta.user.dto.AccountTypeResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountTypeAdminService {

    private final AccountTypeRepository accountTypeRepository;
    private final VideoAssetGroupRepository videoAssetGroupRepository;

    @Transactional
    public AccountTypeResponseDto createAccountType(AccountTypeCreateRequestDto request) {
        String name = normaliseName(request.name());
        if (accountTypeRepository.existsById(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account type already exists: " + name);
        }
        AccountType accountType = new AccountType();
        accountType.setName(name);
        accountType.setVideoGroupIds(new ArrayList<>());
        AccountType saved = accountTypeRepository.save(accountType);
        return toResponse(saved);
    }

    @Transactional
    public AccountTypeResponseDto addVideoGroup(String rawName, UUID videoGroupId) {
        AccountType accountType = findAccountTypeOrThrow(rawName);
        ensureVideoGroupExists(videoGroupId);
        List<UUID> groups = ensureMutableGroupList(accountType);
        if (!groups.contains(videoGroupId)) {
            groups.add(videoGroupId);
            accountType.setVideoGroupIds(groups);
            accountType = accountTypeRepository.save(accountType);
        }
        return toResponse(accountType);
    }

    @Transactional
    public AccountTypeResponseDto removeVideoGroup(String rawName, UUID videoGroupId) {
        AccountType accountType = findAccountTypeOrThrow(rawName);
        List<UUID> groups = ensureMutableGroupList(accountType);
        boolean removed = groups.removeIf(id -> id.equals(videoGroupId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Video group %s not assigned to account type %s".formatted(videoGroupId, accountType.getName()));
        }
        accountType.setVideoGroupIds(groups);
        AccountType updated = accountTypeRepository.save(accountType);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<AccountTypeResponseDto> listAccountTypes() {
        return accountTypeRepository.findAll().stream()
                .map(AccountTypeAdminService::toResponse)
                .toList();
    }

    private AccountType findAccountTypeOrThrow(String rawName) {
        String name = normaliseName(rawName);
        return accountTypeRepository.findById(name)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account type not found: " + name));
    }

    private void ensureVideoGroupExists(UUID videoGroupId) {
        if (!videoAssetGroupRepository.existsById(videoGroupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Video asset group not found: " + videoGroupId);
        }
    }

    private List<UUID> ensureMutableGroupList(AccountType accountType) {
        List<UUID> existing = accountType.getVideoGroupIds();
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account type %s already grants all videos and cannot be scoped to specific groups."
                            .formatted(accountType.getName()));
        }
        return new ArrayList<>(existing);
    }

    private static String normaliseName(String rawName) {
        if (rawName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account type name is required.");
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account type name is required.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static AccountTypeResponseDto toResponse(AccountType accountType) {
        List<UUID> groups = accountType.getVideoGroupIds();
        return new AccountTypeResponseDto(
                accountType.getName(),
                groups == null ? null : List.copyOf(groups)
        );
    }
}
