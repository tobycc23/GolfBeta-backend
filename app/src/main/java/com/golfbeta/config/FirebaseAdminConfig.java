package com.golfbeta.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
public class FirebaseAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminConfig.class);

    @Value("${FIREBASE_SERVICE_ACCOUNT_B64:}")
    private String serviceAccountKey;

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        log.info("FirebaseAuth bean initialised with app={}", app.getName());
        return FirebaseAuth.getInstance(app);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) {
        log.info("FirebaseMessaging bean initialised with app={}", app.getName());
        return FirebaseMessaging.getInstance(app);
    }

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (serviceAccountKey == null || serviceAccountKey.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_B64 is empty; Firebase will not be initialised");
        } else {
            log.info("FIREBASE_SERVICE_ACCOUNT_B64 provided ({} chars)", serviceAccountKey.length());
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            FirebaseApp existing = FirebaseApp.getInstance();
            log.info("FirebaseApp already initialised ({}). Reusing existing instance.", existing.getName());
            return existing;
        }

        log.info("Decoding Firebase service account key…");
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(serviceAccountKey);
        } catch (IllegalArgumentException bad) {
            log.error("Failed to base64-decode FIREBASE_SERVICE_ACCOUNT_B64 (length {}): {}", serviceAccountKey.length(), bad.getMessage());
            throw bad;
        }
        log.info("Decoded Firebase service account payload ({} bytes)", decodedBytes.length);

        GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decodedBytes));
        log.info("Firebase GoogleCredentials created successfully");

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(credentials)
                .build();

        log.info("Initialising FirebaseApp…");
        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("FirebaseApp initialised: {}", app.getName());
        return app;
    }
}
