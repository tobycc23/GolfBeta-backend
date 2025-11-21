package com.golfbeta.user.license;

import com.golfbeta.user.UserProfile;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_video_license",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_video_license_profile_video",
                columnNames = {"user_profile_id", "video_id"}
        ))
@Data
public class UserVideoLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VideoLicenseStatus status = VideoLicenseStatus.ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

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
