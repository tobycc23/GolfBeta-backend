package com.golfbeta.user.asset;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "video_asset_groups")
@Data
public class VideoAssetGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "video_asset_ids", columnDefinition = "uuid[]", nullable = false)
    private List<UUID> videoAssetIds = new ArrayList<>();
}
