package com.golfbeta.video.asset;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_asset", uniqueConstraints = @UniqueConstraint(
        name = "uq_video_asset_video_path",
        columnNames = "video_path"
))
@Data
public class VideoAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "video_path", nullable = false, unique = true)
    private String videoPath;

    @Column(name = "key_hex", nullable = false)
    private String keyHex;

    @Column(name = "key_base64", nullable = false)
    private String keyBase64;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
