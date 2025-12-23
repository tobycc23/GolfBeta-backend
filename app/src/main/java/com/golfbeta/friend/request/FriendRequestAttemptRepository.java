package com.golfbeta.friend.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface FriendRequestAttemptRepository extends JpaRepository<FriendRequestAttempt, Long> {

    @Query("""
            select count(a) from FriendRequestAttempt a
            where a.requesterId = :requesterId
              and a.targetId = :targetId
              and a.attemptedAt >= :since
            """)
    long countRecentAttempts(@Param("requesterId") UUID requesterId,
                             @Param("targetId") UUID targetId,
                             @Param("since") Instant since);
}
