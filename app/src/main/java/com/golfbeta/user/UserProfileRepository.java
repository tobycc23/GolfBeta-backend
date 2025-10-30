package com.golfbeta.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmail(String email);

    @Query(value = """
        SELECT user_id, name, username
        FROM user_profile
        WHERE user_id <> :excludeUid
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
}
