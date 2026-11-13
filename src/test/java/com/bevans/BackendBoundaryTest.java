package com.bevans;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Evita reincidência de staging/projetos mobile dentro do backend. */
class BackendBoundaryTest {
    @Test void repositorioNaoContemArtefatosDeFrontend() throws Exception {
        Path raiz = Path.of("").toAbsolutePath().normalize();
        Set<String> proibidos = Set.of("package.json", "package-lock.json", "app.config.js",
                "metro.config.js", "expo-env.d.ts");
        try (var arquivos = Files.walk(raiz)) {
            var indevidos = arquivos.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(raiz.resolve(".git")))
                    .filter(p -> !p.startsWith(raiz.resolve(".validation-logs")))
                    .filter(p -> {
                        String nome = p.getFileName().toString();
                        return nome.endsWith(".tsx") || nome.endsWith(".jsx") || nome.endsWith(".ts")
                                || nome.endsWith(".js") || nome.endsWith(".cjs") || nome.endsWith(".mjs")
                                || proibidos.contains(nome);
                    }).map(raiz::relativize).toList();
            assertTrue(indevidos.isEmpty(), () -> "Frontend indevido no backend: " + indevidos);
        }
    }
}
