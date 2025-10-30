package com.golfbeta.practice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeHundredRepository extends JpaRepository<PracticeHundred, UUID> {
    Optional<PracticeHundred> findByIdAndUserId(UUID id, String userId);
    List<PracticeHundred> findAllByUserIdOrderByStartedAtDesc(String userId);
    Optional<PracticeHundred> findFirstByUserIdAndCompletedAtIsNullOrderByStartedAtAsc(String userId);
    Optional<PracticeHundred> findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(String userId);
}
