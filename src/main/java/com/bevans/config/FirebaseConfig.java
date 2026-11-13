package com.bevans.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;

@Configuration
@ConditionalOnProperty(
    name = "app.firebase.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class FirebaseConfig {

    @Value("${app.firebase.credentials-path:/home/ubuntu/firebase-service-account.json}")
    private String credentialsPath;

    @PostConstruct
    public void initialize() {
        try {
            FirebaseOptions options;

            try (FileInputStream serviceAccount =
                    new FileInputStream(credentialsPath)) {
                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inicializar o Firebase: " + e.getMessage(), e);
        }
    }
}