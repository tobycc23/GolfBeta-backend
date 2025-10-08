package com.golfbeta.user;

import com.golfbeta.converter.ImprovementAreasConverter;
import com.golfbeta.enums.ImprovementAreas;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "user_profile")
@Data
public class UserProfile {
    @Id
    @Column(name="user_id")
    private String userId;
    @Column(nullable=false)
    private String email;
    @Column(name="favourite_colour")
    private String favouriteColour;
    @Column(name="created_at", nullable=false)
    private Instant createdAt = Instant.now();
    @Column(name="updated_at", nullable=false)
    private Instant updatedAt = Instant.now();

    private String name;
    private LocalDate dob;
    @Column(unique = true)
    private String username;
    @Column(name="golf_handicap")
    private Double golfHandicap;
    @Column(name="break_number_target")
    private Integer breakNumberTarget;
    @Column(name="skill_level")
    private String skillLevel;

    @Column(columnDefinition = "text[]", name="improvement_areas")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> improvementAreas;

    @Column(name="profile_completed_at")
    private Instant profileCompletedAt;
    @Column(name="profile_version", nullable=false)
    private Integer profileVersion = 1;
}
