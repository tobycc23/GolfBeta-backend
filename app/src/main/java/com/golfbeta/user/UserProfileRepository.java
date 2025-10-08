package com.golfbeta.user;

// src/main/java/com/yourco/golfbeta/user/UserProfileRepo.java
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmail(String email);
}
