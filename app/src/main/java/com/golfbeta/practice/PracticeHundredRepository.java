package com.golfbeta.practice;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeHundredRepository extends JpaRepository<PracticeHundred, UUID> {
    Optional<PracticeHundred> findByIdAndUserId(UUID id, UUID userId);
    List<PracticeHundred> findAllByUserIdOrderByStartedAtDesc(UUID userId);
    Optional<PracticeHundred> findFirstByUserIdAndCompletedAtIsNullOrderByStartedAtAsc(UUID userId);
    Optional<PracticeHundred> findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(UUID userId);
    List<PracticeHundred> findByUserIdAndCompletedAtIsNotNull(UUID userId, Pageable pageable);
}
