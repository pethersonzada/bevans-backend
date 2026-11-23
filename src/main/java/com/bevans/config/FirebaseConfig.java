package com.bevans.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(
    name = "app.firebase.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class FirebaseConfig {

    @Value("${app.firebase.credentials-path:/home/ubuntu/firebase-service-account.json}")
    private String credentialsPath;

    // Credencial em variável privada do servidor, sem arquivo no repositório.
    @Value("${app.firebase.credentials-json:}")
    private String credentialsJson;

    static InputStream abrirCredencial(String json, String caminho) throws IOException {
        if (json != null && !json.isBlank()) {
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        if (caminho == null || caminho.isBlank()) {
            throw new IOException("Credencial Firebase Admin não configurada.");
        }
        return new FileInputStream(caminho);
    }

    @PostConstruct
    public void initialize() {
        try {
            FirebaseOptions options;
            try (InputStream serviceAccount = abrirCredencial(credentialsJson, credentialsPath)) {
                options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        } catch (Exception e) {
            // Não incluir conteúdo, caminho de segredo ou JSON na mensagem de falha.
            throw new IllegalStateException("Falha ao inicializar Firebase Admin.", e);
        }
    }
}