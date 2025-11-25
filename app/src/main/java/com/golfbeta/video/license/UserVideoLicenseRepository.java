package com.golfbeta.video.license;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserVideoLicenseRepository extends JpaRepository<UserVideoLicense, UUID> {

    Optional<UserVideoLicense> findByUserProfileFirebaseIdAndVideoId(String firebaseId, String videoId);
}
