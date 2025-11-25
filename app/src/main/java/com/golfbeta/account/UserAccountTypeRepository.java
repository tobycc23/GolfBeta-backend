package com.golfbeta.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountTypeRepository extends JpaRepository<UserAccountType, UUID> {
    Optional<UserAccountType> findByUserProfileFirebaseId(String firebaseId);
    boolean existsByUserProfileFirebaseId(String firebaseId);
}
