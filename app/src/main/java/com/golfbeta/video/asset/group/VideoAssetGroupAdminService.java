package com.golfbeta.video.asset.group;

import com.golfbeta.video.asset.VideoAssetRepository;
import com.golfbeta.video.asset.group.dto.VideoGroupAssetRequestDto;
import com.golfbeta.video.asset.group.dto.VideoGroupCreateRequestDto;
import com.golfbeta.video.asset.group.dto.VideoGroupResponseDto;
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
public class VideoAssetGroupAdminService {

    private final VideoAssetGroupRepository groupRepository;
    private final VideoAssetRepository videoAssetRepository;

    @Transactional
    public VideoGroupResponseDto createVideoGroup(VideoGroupCreateRequestDto request) {
        String name = normaliseName(request.name());
        VideoAssetGroup group = new VideoAssetGroup();
        group.setName(name);
        group.setVideoAssetIds(new ArrayList<>());
        VideoAssetGroup saved = groupRepository.save(group);
        return toResponse(saved);
    }

    @Transactional
    public VideoGroupResponseDto addVideoAsset(UUID groupId, VideoGroupAssetRequestDto request) {
        VideoAssetGroup group = findGroupOrThrow(groupId);
        ensureVideoAssetExists(request.videoAssetId());
        List<UUID> assets = ensureMutableAssets(group);
        if (!assets.contains(request.videoAssetId())) {
            assets.add(request.videoAssetId());
            group.setVideoAssetIds(assets);
            group = groupRepository.save(group);
        }
        return toResponse(group);
    }

    @Transactional
    public VideoGroupResponseDto removeVideoAsset(UUID groupId, UUID videoAssetId) {
        VideoAssetGroup group = findGroupOrThrow(groupId);
        List<UUID> assets = ensureMutableAssets(group);
        boolean removed = assets.removeIf(id -> id.equals(videoAssetId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Video asset %s not present in group %s".formatted(videoAssetId, groupId));
        }
        group.setVideoAssetIds(assets);
        VideoAssetGroup updated = groupRepository.save(group);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<VideoGroupResponseDto> searchByName(String query) {
        String sanitized = query == null ? "" : query.trim();
        List<VideoAssetGroup> results = groupRepository
                .findTop50ByNameContainingIgnoreCaseOrderByNameAsc(sanitized);
        return results.stream().map(VideoAssetGroupAdminService::toResponse).toList();
    }

    private VideoAssetGroup findGroupOrThrow(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Video asset group not found: " + id));
    }

    private void ensureVideoAssetExists(UUID videoAssetId) {
        if (!videoAssetRepository.existsById(videoAssetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Video asset not found: " + videoAssetId);
        }
    }

    private static List<UUID> ensureMutableAssets(VideoAssetGroup group) {
        List<UUID> assets = group.getVideoAssetIds();
        return assets == null ? new ArrayList<>() : new ArrayList<>(assets);
    }

    private static String normaliseName(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video group name is required.");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video group name is required.");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static VideoGroupResponseDto toResponse(VideoAssetGroup group) {
        List<UUID> ids = group.getVideoAssetIds();
        return new VideoGroupResponseDto(
                group.getId(),
                group.getName(),
                ids == null ? List.of() : List.copyOf(ids)
        );
    }
}
