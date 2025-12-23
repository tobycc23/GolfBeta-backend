package com.golfbeta.friend.request;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "friend_request_attempt",
        indexes = {
                @Index(name = "idx_friend_request_attempt_recent", columnList = "requester_id,target_id,attempted_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class FriendRequestAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false, columnDefinition = "uuid")
    private UUID requesterId;

    @Column(name = "target_id", nullable = false, columnDefinition = "uuid")
    private UUID targetId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();
}
