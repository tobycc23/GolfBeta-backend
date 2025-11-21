package com.golfbeta.user.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoAssetRepository extends JpaRepository<VideoAsset, UUID> {
    Optional<VideoAsset> findByVideoPath(String videoPath);
    List<VideoAsset> findTop50ByVideoPathContainingIgnoreCaseOrderByVideoPathAsc(String videoPath);
}
