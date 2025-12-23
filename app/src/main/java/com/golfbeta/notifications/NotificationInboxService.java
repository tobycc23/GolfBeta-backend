package com.golfbeta.notifications;

import com.golfbeta.notifications.dto.NotificationInboxDto;
import com.golfbeta.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private static final int MAX_RETURN = 30;

    private final NotificationInboxRepository repo;
    private final UserProfileRepository profiles;

    public List<NotificationInboxDto> listForUser(String firebaseId) {
        var user = profiles.findByFirebaseId(firebaseId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return repo.findLatest(user.getId()).stream()
                .limit(MAX_RETURN)
                .map(this::toDto)
                .toList();
    }

    public void markSeen(String firebaseId, Long notificationId) {
        var user = profiles.findByFirebaseId(firebaseId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        repo.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(user.getId())) {
                n.setSeen(true);
                repo.save(n);
            }
        });
    }

    public NotificationInbox create(UUID userId, NotificationType type, String message, UUID fromUserId) {
        var n = new NotificationInbox();
        n.setUserId(userId);
        n.setType(type);
        n.setMessage(message);
        n.setCreatedAt(Instant.now());
        n.setSeen(false);
        n.setFromUserId(fromUserId);
        return repo.save(n);
    }

    private NotificationInboxDto toDto(NotificationInbox n) {
        String fromFirebase = null;
        if (n.getFromUserId() != null) {
            fromFirebase = profiles.findById(n.getFromUserId()).map(p -> p.getFirebaseId()).orElse(null);
        }
        return new NotificationInboxDto(n.getId(), n.getType(), n.getMessage(), n.getCreatedAt(), n.isSeen(), fromFirebase);
    }
}
