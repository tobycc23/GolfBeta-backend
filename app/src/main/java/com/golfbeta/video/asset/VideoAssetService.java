package com.golfbeta.video.asset;

import com.golfbeta.video.VideoPathUtils;
import com.golfbeta.video.asset.dto.VideoAssetRequestDto;
import com.golfbeta.video.asset.dto.VideoAssetResponseDto;
import com.golfbeta.video.asset.dto.VideoAssetSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VideoAssetService {

    private static final int EXPECTED_KEY_BYTES = 16;

    private final VideoAssetRepository repository;

    @Transactional
    public VideoAssetResponseDto upsert(VideoAssetRequestDto request) {
        String videoPath = VideoPathUtils.normalise(request.videoPath());
        String keyHex = normaliseHex(request.keyHex());
        String keyBase64 = normaliseBase64(request.keyBase64());
        validateKeyMaterial(keyHex, keyBase64);

        VideoAsset asset = repository.findByVideoPath(videoPath)
                .orElseGet(VideoAsset::new);
        asset.setVideoPath(videoPath);
        asset.setKeyHex(keyHex);
        asset.setKeyBase64(keyBase64);
        asset.setKeyVersion(request.keyVersion() != null ? request.keyVersion() : asset.getKeyVersion());
        if (asset.getKeyVersion() == null || asset.getKeyVersion() < 1) {
            asset.setKeyVersion(1);
        }

        VideoAsset saved = repository.save(asset);
        return toResponse(saved);
    }

    @Transactional
    public boolean delete(String videoPath) {
        String normalised = VideoPathUtils.normalise(videoPath);
        return repository.findByVideoPath(normalised)
                .map(asset -> {
                    repository.delete(asset);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public byte[] resolveKeyBytesOrThrow(String videoPath) {
        String normalised = VideoPathUtils.normalise(videoPath);
        VideoAsset asset = repository.findByVideoPath(normalised)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video asset not registered: " + normalised));
        byte[] decoded = Base64.getDecoder().decode(asset.getKeyBase64());
        if (decoded.length != EXPECTED_KEY_BYTES) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored key for %s is not %d bytes".formatted(asset.getVideoPath(), EXPECTED_KEY_BYTES));
        }
        return decoded;
    }

    @Transactional(readOnly = true)
    public List<VideoAssetSummaryDto> searchByVideoPath(String query) {
        String sanitized = query == null ? "" : query.trim();
        List<VideoAsset> results;
        if (sanitized.isEmpty()) {
            results = repository.findTop50ByVideoPathContainingIgnoreCaseOrderByVideoPathAsc("");
        } else {
            results = repository.findTop50ByVideoPathContainingIgnoreCaseOrderByVideoPathAsc(sanitized);
        }
        return results.stream()
                .map(asset -> new VideoAssetSummaryDto(asset.getId(), asset.getVideoPath()))
                .toList();
    }

    private static String normaliseHex(String keyHex) {
        return keyHex.trim().toUpperCase(Locale.ROOT);
    }

    private static String normaliseBase64(String keyBase64) {
        return keyBase64.trim();
    }

    private static void validateKeyMaterial(String keyHex, String keyBase64) {
        if (keyHex.length() != 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyHex must be 32 characters");
        }
        if (!keyHex.matches("^[0-9A-F]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyHex must be uppercase hex characters");
        }
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length != EXPECTED_KEY_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "keyBase64 must decode to %d bytes".formatted(EXPECTED_KEY_BYTES));
        }
    }

    private static VideoAssetResponseDto toResponse(VideoAsset asset) {
        return new VideoAssetResponseDto(
                asset.getId(),
                asset.getVideoPath(),
                asset.getKeyVersion(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
