package com.golfbeta.user.license;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserVideoLicenseRepository extends JpaRepository<UserVideoLicense, UUID> {

    Optional<UserVideoLicense> findByUserProfileUserIdAndVideoId(String userId, String videoId);
}
