package com.golfbeta.friend;

import com.golfbeta.friend.enums.FriendStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    Optional<Friend> findByUserIdAAndUserIdB(UUID a, UUID b);

    @Query("""
           select f from Friend f
           where (f.userIdA = :uid or f.userIdB = :uid) and f.status = :status
           order by f.createdAt desc
           """)
    List<Friend> findAllByUidAndStatus(UUID uid, FriendStatus status);

    @Query("""
           select f from Friend f
           where (f.userIdA = :uid or f.userIdB = :uid)
           order by f.createdAt desc
           """)
    List<Friend> findAllByUid(UUID uid);

    @Query(value = """
        SELECT
          other.firebase_id AS other_user_id,
          other.name        AS other_name,
          other.username    AS other_username,
          f.status          AS status,
          (f.requester_id = :uid) AS requested_by_me,
          f.created_at      AS since
        FROM friends f
        JOIN user_profile other
          ON other.id = CASE WHEN f.user_id_a = :uid THEN f.user_id_b ELSE f.user_id_a END
        WHERE (f.user_id_a = :uid OR f.user_id_b = :uid)
          AND f.status = 'FRIENDS'
        ORDER BY COALESCE(other.name, '') ASC, f.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findFriendsForList(@Param("uid") UUID uid);

    @Query(value = """
        SELECT
          other.firebase_id AS other_user_id,
          other.name        AS other_name,
          other.username    AS other_username,
          f.status          AS status,
          (f.requester_id = :uid) AS requested_by_me,
          f.created_at      AS since
        FROM friends f
        JOIN user_profile other
          ON other.id = CASE WHEN f.user_id_a = :uid THEN f.user_id_b ELSE f.user_id_a END
        WHERE (f.user_id_a = :uid OR f.user_id_b = :uid)
          AND f.status = 'REQUESTED'
          AND f.requester_id <> :uid   -- inbound requests only
        ORDER BY f.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findIncomingForList(@Param("uid") UUID uid);

    @Query(value = """
        SELECT
          other.firebase_id AS other_user_id,
          other.name        AS other_name,
          other.username    AS other_username,
          f.status          AS status,
          (f.requester_id = :uid) AS requested_by_me,
          f.created_at      AS since
        FROM friends f
        JOIN user_profile other
          ON other.id = CASE WHEN f.user_id_a = :uid THEN f.user_id_b ELSE f.user_id_a END
        WHERE (f.user_id_a = :uid OR f.user_id_b = :uid)
          AND f.status = 'REQUESTED'
          AND f.requester_id = :uid     -- outgoing requests (useful for annotating search results)
        ORDER BY f.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findOutgoingForList(@Param("uid") UUID uid);
}
