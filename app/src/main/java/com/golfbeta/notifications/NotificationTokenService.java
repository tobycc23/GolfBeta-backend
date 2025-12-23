package com.golfbeta.notifications;

import com.golfbeta.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(NotificationTokenService.class);

    private final DeviceTokenRepository tokens;
    private final UserProfileRepository profiles;

    @Transactional
    public void register(String firebaseId, String token, String platform) {
        var user = profiles.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (token == null || token.isBlank()) {
            log.warn("Attempted to register empty token for user {}", firebaseId);
            return;
        }

        tokens.findByToken(token).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (!existing.getUserProfile().getId().equals(user.getId())) {
                existing.setUserProfile(user);
                changed = true;
            }
            if (platform != null && !platform.equals(existing.getPlatform())) {
                existing.setPlatform(platform);
                changed = true;
            }
            if (changed) tokens.save(existing);
            log.debug("Device token reused for user {} platform {}", firebaseId, platform);
        }, () -> {
            var dt = new DeviceToken();
            dt.setUserProfile(user);
            dt.setToken(token);
            dt.setPlatform(platform);
            tokens.save(dt);
            log.debug("Device token registered for user {} platform {} prefix {}", firebaseId, platform, tokenPrefix(token));
        });
    }

    @Transactional
    public void unregister(String token) {
        tokens.deleteByToken(token);
        log.debug("Device token unregistered prefix {}", tokenPrefix(token));
    }

    private String tokenPrefix(String token) {
        if (token == null) return "null";
        return token.length() <= 10 ? token : token.substring(0, 10);
    }
}
