package com.bevans.security;

import com.bevans.controller.UsuarioController;
import com.bevans.service.UsuarioService;
import com.bevans.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class EmailFirebaseHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean UsuarioService service;
    @Autowired ObjectMapper json;

    private MockedStatic<FirebaseAuth> firebaseEmail(String uid, String email, boolean verificado)
            throws Exception {
        var auth = mock(FirebaseAuth.class);
        var token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.getClaims()).thenReturn(Map.of("email_verified", verificado));
        when(auth.verifyIdToken("valido", true)).thenReturn(token);
        var escopo = mockStatic(FirebaseAuth.class);
        escopo.when(FirebaseAuth::getInstance).thenReturn(auth);
        return escopo;
    }

    private String corpo(String email) throws Exception {
        return json.writeValueAsString(Map.of("email", email));
    }

    @Test void sincronizaSomenteEmailVerificadoDoProprioFirebase() throws Exception {
        Usuario dono = usuario(1, "PASSAGEIRO");
        dono.setEmail("novo@example.test");
        identidade("dono", dono);
        when(service.atualizarEmail(1L, "novo@example.test")).thenReturn(dono);

        try (var ignored = firebaseEmail("dono", "novo@example.test", true)) {
            mvc.perform(put("/usuarios/1/email")
                    .header("Authorization", "Bearer valido")
                    .contentType("application/json")
                    .content(corpo("NOVO@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@example.test"));
        }
        verify(service).atualizarEmail(1L, "novo@example.test");
    }

    @Test void tokenAntigoNaoAutorizaEmailDiferente() throws Exception {
        identidade("dono", usuario(1, "PASSAGEIRO"));
        try (var ignored = firebaseEmail("dono", "antigo@example.test", true)) {
            mvc.perform(put("/usuarios/1/email")
                    .header("Authorization", "Bearer valido")
                    .contentType("application/json")
                    .content(corpo("novo@example.test")))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void emailNaoVerificadoNaoAtualizaBanco() throws Exception {
        identidade("dono", usuario(1, "PASSAGEIRO"));
        try (var ignored = firebaseEmail("dono", "novo@example.test", false)) {
            mvc.perform(put("/usuarios/1/email")
                    .header("Authorization", "Bearer valido")
                    .contentType("application/json")
                    .content(corpo("novo@example.test")))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void emailAusenteNoTokenNaoAtualizaBanco() throws Exception {
        identidade("dono", usuario(1, "PASSAGEIRO"));
        try (var ignored = firebaseEmail("dono", null, true)) {
            mvc.perform(put("/usuarios/1/email")
                    .header("Authorization", "Bearer valido")
                    .contentType("application/json")
                    .content(corpo("novo@example.test")))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void outroUidNaoAtualizaPerfil() throws Exception {
        identidade("intruso", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebaseEmail("intruso", "novo@example.test", true)) {
            mvc.perform(put("/usuarios/1/email")
                    .header("Authorization", "Bearer valido")
                    .contentType("application/json")
                    .content(corpo("novo@example.test")))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void corpoSemEmailRetorna400() throws Exception {
        identidade("dono", usuario(1, "PASSAGEIRO"));
        try (var ignored = firebaseEmail("dono", "novo@example.test", true)) {
            mvc.perform(put("/usuarios/1/email")
                    .header("Authorization", "Bearer valido")
                    .contentType("application/json")
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void ausenciaDeTokenRetorna401() throws Exception {
        mvc.perform(put("/usuarios/1/email")
                .contentType("application/json")
                .content(corpo("novo@example.test")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
