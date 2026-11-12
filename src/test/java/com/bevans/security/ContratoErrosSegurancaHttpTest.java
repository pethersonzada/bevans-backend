package com.bevans.security;

import com.bevans.controller.TurmaController;
import com.bevans.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TurmaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class ContratoErrosSegurancaHttpTest extends BaseAutorizacaoHttpTest {

    private void conferirErro(
            org.springframework.test.web.servlet.ResultActions resultado,
            int codigo
    ) throws Exception {
        resultado
            .andExpect(status().is(codigo))
            .andExpect(content().contentTypeCompatibleWith(
                    "application/json"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(codigo))
            .andExpect(jsonPath("$.erro").isString())
            .andExpect(jsonPath("$.caminho")
                    .value("/turmas/13/passageiros"));
    }

    @Test
    void semTokenRetorna401Padronizado() throws Exception {
        conferirErro(
            mvc.perform(get("/turmas/13/passageiros")),
            401
        );
    }

    @Test
    void tokenInvalidoRetorna401Padronizado() throws Exception {
        conferirErro(
            mvc.perform(get("/turmas/13/passageiros")
                    .header("Authorization", "Bearer ")),
            401
        );
    }

    @Test
    void usuarioSemPermissaoRetorna403Padronizado()
            throws Exception {

        Usuario passageiro = usuario(2L, "PASSAGEIRO");
        identidade("passageiro-2", passageiro);

        try (var scope = firebase("valido", "passageiro-2")) {
            conferirErro(
                mvc.perform(get("/turmas/13/passageiros")
                        .header("Authorization", "Bearer valido")),
                403
            );
        }
    }
}
