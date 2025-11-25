package com.golfbeta.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "golfr_practice_hundred")
@Data
public class PracticeHundred {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "putting_3ft", columnDefinition = "TEXT")
    private String putting3ft;
    @Column(name = "putting_6ft", columnDefinition = "TEXT")
    private String putting6ft;
    @Column(name = "putting_15ft", columnDefinition = "TEXT")
    private String putting15ft;
    @Column(name = "chipping_10yards", columnDefinition = "TEXT")
    private String chipping10yards;
    @Column(name = "chipping_20yards", columnDefinition = "TEXT")
    private String chipping20yards;
    @Column(name = "pitching_fullpw", columnDefinition = "TEXT")
    private String pitchingFullpw;
    @Column(name = "pitching_threequarterpw", columnDefinition = "TEXT")
    private String pitchingThreequarterpw;
    @Column(name = "pitching_highlobs", columnDefinition = "TEXT")
    private String pitchingHighlobs;
    @Column(name = "shortirons_straight", columnDefinition = "TEXT")
    private String shortironsStraight;
    @Column(name = "shortirons_draw", columnDefinition = "TEXT")
    private String shortironsDraw;
    @Column(name = "shortirons_fade", columnDefinition = "TEXT")
    private String shortironsFade;
    @Column(name = "longirons_straight", columnDefinition = "TEXT")
    private String longironsStraight;
    @Column(name = "longirons_draw", columnDefinition = "TEXT")
    private String longironsDraw;
    @Column(name = "longirons_fade", columnDefinition = "TEXT")
    private String longironsFade;
    @Column(name = "woods_straight", columnDefinition = "TEXT")
    private String woodsStraight;
    @Column(name = "woods_draw", columnDefinition = "TEXT")
    private String woodsDraw;
    @Column(name = "woods_fade", columnDefinition = "TEXT")
    private String woodsFade;
    @Column(name = "driving_straight", columnDefinition = "TEXT")
    private String drivingStraight;
    @Column(name = "driving_draw", columnDefinition = "TEXT")
    private String drivingDraw;
    @Column(name = "driving_fade", columnDefinition = "TEXT")
    private String drivingFade;
}
