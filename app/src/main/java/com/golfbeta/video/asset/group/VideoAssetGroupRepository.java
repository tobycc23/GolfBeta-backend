package com.golfbeta.video.asset.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VideoAssetGroupRepository extends JpaRepository<VideoAssetGroup, UUID> {
    List<VideoAssetGroup> findTop50ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
