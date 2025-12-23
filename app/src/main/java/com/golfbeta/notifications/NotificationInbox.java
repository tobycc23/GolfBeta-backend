package com.golfbeta.notifications;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications_inbox",
        indexes = @Index(name = "idx_notifications_inbox_user_created_at", columnList = "user_id, created_at DESC"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "notification_type", nullable = false, columnDefinition = "notification_type")
    private NotificationType type;

    @Column(name = "notification_message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "seen", nullable = false)
    private boolean seen = false;

    @Column(name = "from_user_id", columnDefinition = "uuid")
    private UUID fromUserId;
}
