package com.golfbeta.notifications;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
    List<DeviceToken> findAllByUserProfileId(UUID userProfileId);
    Optional<DeviceToken> findByToken(String token);
    void deleteByToken(String token);
}
