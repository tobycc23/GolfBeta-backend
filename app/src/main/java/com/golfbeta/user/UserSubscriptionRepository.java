package com.golfbeta.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    boolean existsByUserProfileUserId(String userId);
    Optional<UserSubscription> findByUserProfileUserId(String userId);
}

