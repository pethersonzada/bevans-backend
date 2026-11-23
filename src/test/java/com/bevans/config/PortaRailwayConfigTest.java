package com.bevans.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PortaRailwayConfigTest {

    @Test
    void aceitaPortaDaPlataformaComFallbackLocal() throws Exception {
        try (InputStream arquivo = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(arquivo, "application.properties deve estar no classpath");
            Properties configuracao = new Properties();
            configuracao.load(arquivo);

            assertEquals("${PORT:8080}", configuracao.getProperty("server.port"));
            assertEquals("${HIBERNATE_DDL_AUTO:validate}",
                    configuracao.getProperty("spring.jpa.hibernate.ddl-auto"));
        }
    }
}
