package com.golfbeta.friend;

import com.golfbeta.enums.FriendStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "friends",
        uniqueConstraints = @UniqueConstraint(name = "friends_unique_pair", columnNames = {"user_id_a","user_id_b"})
)
@Getter @Setter @NoArgsConstructor
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id_a", nullable=false)
    private String userIdA;

    @Column(name="user_id_b", nullable=false)
    private String userIdB;

    @Column(name="requester_id", nullable=false)
    private String requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false)
    private FriendStatus status = FriendStatus.REQUESTED;

    @Column(name="created_at", nullable=false)
    private Instant createdAt = Instant.now();

    @Column(name="updated_at", nullable=false)
    private Instant updatedAt = Instant.now();

    public String otherOf(String uid) {
        if (uid.equals(userIdA)) return userIdB;
        if (uid.equals(userIdB)) return userIdA;
        throw new IllegalArgumentException("User is not part of this friendship");
    }

    public boolean involves(String uid) {
        return uid.equals(userIdA) || uid.equals(userIdB);
    }
}
