package com.bevans.security;

import com.bevans.controller.TurmaController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TurmaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
@TestPropertySource(properties = "app.cors.allowed-origins=https://app.exemplo.test")
class CorsSegurancaHttpTest extends BaseAutorizacaoHttpTest {
    @Test void preflightOrigemAutorizada() throws Exception {
        mvc.perform(options("/turmas/13/passageiros")
                .header("Origin", "https://app.exemplo.test")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://app.exemplo.test"));
    }
    @Test void preflightOrigemNaoAutorizada() throws Exception {
        mvc.perform(options("/turmas/13/passageiros")
                .header("Origin", "https://hostil.exemplo.test")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.erro").isString())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.caminho")
                    .value("/turmas/13/passageiros"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    @Test void origemLocalNaoEhPermitidaQuandoAmbienteRestrito() throws Exception {
        mvc.perform(options("/turmas/13/passageiros")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.erro").isString())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.caminho")
                    .value("/turmas/13/passageiros"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
