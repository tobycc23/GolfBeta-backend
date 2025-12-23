package com.golfbeta.notifications;

import com.golfbeta.user.UserProfile;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final DeviceTokenRepository tokens;
    private final FirebaseMessaging firebaseMessaging;

    public void sendFriendRequest(UserProfile requester, UserProfile recipient) {
        List<DeviceToken> targetTokens = tokens.findAllByUserProfileId(recipient.getId());
        if (targetTokens.isEmpty()) {
            log.debug("No device tokens for user {}; skipping friend request push", recipient.getFirebaseId());
            return;
        }

        Map<String, String> data = new HashMap<>();
        data.put("type", "friend_request");
        if (requester.getName() != null) data.put("fromName", requester.getName());
        if (requester.getUsername() != null) data.put("fromUsername", requester.getUsername());
        data.put("fromUserId", requester.getFirebaseId());

        for (DeviceToken token : targetTokens) {
            try {
                Message message = Message.builder()
                        .setToken(token.getToken())
                        .putAllData(data)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId("golfbeta-general")
                                        .build())
                                .build())
                        .setNotification(Notification.builder()
                                .setTitle(null)
                                .setBody(buildBody(requester))
                                .build())
                        .build();
                firebaseMessaging.sendAsync(message).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Push send interrupted for token {}: {}", token.getToken(), ie.getMessage());
            } catch (ExecutionException | TimeoutException ex) {
                log.warn("Failed to send friend request push to token {}: {}", token.getToken(), ex.getMessage());
            }
        }
    }

    private String buildBody(UserProfile requester) {
        String name = requester.getName();
        if (name == null || name.isBlank()) {
            name = requester.getUsername();
        }
        if (name == null || name.isBlank()) {
            name = "Someone";
        }
        return "Friend request from: " + name;
    }
}
