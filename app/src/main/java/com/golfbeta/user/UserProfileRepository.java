package com.golfbeta.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByEmail(String email);
    Optional<UserProfile> findByFirebaseId(String firebaseId);

    @Query(value = """
        SELECT firebase_id, name, username
        FROM user_profile
        WHERE firebase_id <> :excludeUid
          AND name IS NOT NULL
          AND (
               name ILIKE CONCAT('%', :q, '%')
            OR similarity(name, :q) > 0.3
          )
        ORDER BY GREATEST(similarity(name, :q), word_similarity(name, :q)) DESC,
                 name ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchByNameFuzzy(@Param("q") String query,
                                     @Param("excludeUid") String excludeUid,
                                     @Param("limit") int limit);

    @Query(value = """
        SELECT firebase_id, email, name
        FROM user_profile
        WHERE name IS NOT NULL
          AND (
               :q IS NULL
            OR :q = ''
            OR name ILIKE CONCAT('%', :q, '%')
            OR similarity(name, :q) > 0.3
          )
        ORDER BY GREATEST(similarity(name, :q), word_similarity(name, :q)) DESC,
                 name ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> adminSearchByName(@Param("q") String query,
                                     @Param("limit") int limit);
}
