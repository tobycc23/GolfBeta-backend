package com.golfbeta.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UsernameAllocator {

    private final JdbcTemplate jdbc;

    public UsernameAllocator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Create a slug like "toby" from "Toby Smith" -> "toby"
    private String toBasePrefix(String name) {
        if (name == null || name.isBlank()) return "user";
        String[] parts = name.trim().split("\\s+"); // split on spaces
        String first = parts[0].toLowerCase()
                .replaceAll("[^a-z0-9]", ""); // keep only letters/numbers
        return first.isBlank() ? "user" : first;
    }

    @Transactional
    public String allocateUsername(String name) {
        String base = toBasePrefix(name);
        // Upsert counter row atomically and get the allocated sequence.
        // If row doesn't exist: start at 1, else increment.
        Integer seq = jdbc.queryForObject("""
      WITH upsert AS (
        INSERT INTO username_counters (base_prefix, curr_seq)
        VALUES (?, 1)                    -- we will use 1 now, next will be 2
        ON CONFLICT (base_prefix)
        DO UPDATE SET curr_seq = username_counters.curr_seq + 1
        RETURNING CASE WHEN xmax = 0 THEN 1 ELSE (username_counters.curr_seq) END AS allocated
      )
      SELECT allocated FROM upsert
      """, Integer.class, base);

        String candidate = base + String.format("%05d", seq);

        // The unique index on user_profile.username is our last line of defense.
        // In the extremely unlikely case of collision, loop a few times.
        int attempts = 0;
        while (existsUsername(candidate) && attempts < 3) {
            seq = jdbc.queryForObject("""
        UPDATE username_counters
        SET curr_seq = curr_seq + 1
        WHERE base_prefix = ?
        RETURNING curr_seq - 1
        """, Integer.class, base);
            candidate = base + String.format("%05d", seq);
            attempts++;
        }
        return candidate;
    }

    private boolean existsUsername(String u) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_profile WHERE username = ?", Integer.class, u);
        return count != null && count > 0;
    }
}

