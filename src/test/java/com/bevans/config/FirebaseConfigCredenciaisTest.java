package com.bevans.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirebaseConfigCredenciaisTest {

    @TempDir
    Path temporario;

    @Test
    void privilegiaVariavelPrivadaSemCriarArquivoOuConsultarRede() throws Exception {
        byte[] esperado = "{\"type\":\"service_account\",\"project_id\":\"projeto-ficticio\"}"
                .getBytes(StandardCharsets.UTF_8);
        try (InputStream credencial = FirebaseConfig.abrirCredencial(
                new String(esperado, StandardCharsets.UTF_8),
                temporario.resolve("inexistente.json").toString())) {
            assertArrayEquals(esperado, credencial.readAllBytes());
        }
    }

    @Test
    void preservaArquivoLegadoQuandoVariavelNaoFoiConfigurada() throws Exception {
        Path arquivo = temporario.resolve("credencial-de-teste.json");
        Files.writeString(arquivo, "{\"teste\":true}");
        try (InputStream credencial = FirebaseConfig.abrirCredencial(" ", arquivo.toString())) {
            assertArrayEquals(Files.readAllBytes(arquivo), credencial.readAllBytes());
        }
    }

    @Test
    void recusaCredencialAusenteSemLogarValorDoSegredo() {
        Exception erro = assertThrows(Exception.class,
                () -> FirebaseConfig.abrirCredencial("", ""));
        assertTrue(erro.getMessage().contains("não configurada"));
    }
}
