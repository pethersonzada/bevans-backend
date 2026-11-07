package com.bevans.security;

import com.bevans.controller.TurmaController;
import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.AlunoTurmaRepository;
import com.bevans.repository.EnderecoRepository;
import com.bevans.repository.PresencaRepository;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.service.TurmaService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Testa a cadeia HTTP real (FirebaseAuthenticationFilter + @PreAuthorize), sem banco ou credenciais reais. */
@WebMvcTest(controllers = TurmaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class TurmaAutorizacaoHttpTest {
    @Autowired MockMvc mvc;

    @MockBean TurmaService turmaService;
    @MockBean PresencaRepository presencas;
    @MockBean UsuarioRepository usuarios;
    @MockBean TurmaRepository turmas;
    @MockBean AlunoTurmaRepository vinculos;
    @MockBean EnderecoRepository enderecos;

    private Usuario usuario(long id, String tipo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setTipo(tipo);
        return u;
    }

    private void identidade(String uid, Usuario u) {
        when(usuarios.findByFirebaseUid(uid)).thenReturn(Optional.of(u));
    }

    private void turma(long id, Usuario dono) {
        Turma t = new Turma();
        t.setId(id);
        t.setMotorista(dono);
        when(turmas.findById(id)).thenReturn(Optional.of(t));
    }

    private MockedStatic<FirebaseAuth> firebase(String token, String uid) throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getUid()).thenReturn(uid);
        when(auth.verifyIdToken(token)).thenReturn(decoded);
        MockedStatic<FirebaseAuth> scope = mockStatic(FirebaseAuth.class);
        scope.when(FirebaseAuth::getInstance).thenReturn(auth);
        return scope;
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/turmas/13/passageiros"))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(turmaService);
    }

    @Test
    void cabecalhoBearerVazioRecebe401() throws Exception {
        mvc.perform(get("/turmas/13/passageiros").header("Authorization", "Bearer "))
           .andExpect(status().isUnauthorized());
        verifyNoInteractions(turmaService);
    }

    @Test
    void tokenInvalidoRecebe401() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        when(auth.verifyIdToken("invalido")).thenThrow(new IllegalArgumentException("token inválido"));
        try (MockedStatic<FirebaseAuth> scope = mockStatic(FirebaseAuth.class)) {
            scope.when(FirebaseAuth::getInstance).thenReturn(auth);
            mvc.perform(get("/turmas/13/passageiros")
                    .header("Authorization", "Bearer invalido"))
               .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(turmaService);
    }

    @Test
    void motoristaDonoLePassageirosDaPropriaTurma() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        identidade("motorista-1", dono);
        turma(13, dono);
        when(turmaService.listarPassageirosPorTurma(13L)).thenReturn(List.of());
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(get("/turmas/13/passageiros")
                    .header("Authorization", "Bearer valido"))
               .andExpect(status().isOk())
               .andExpect(content().json("[]"));
        }
        verify(turmaService).listarPassageirosPorTurma(13L);
    }

    @Test
    void motoristaNaoPodeLerPassageirosDeOutraTurma() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        Usuario outroDono = usuario(4, "MOTORISTA");
        identidade("motorista-1", dono);
        turma(3, outroDono);
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(get("/turmas/3/passageiros")
                    .header("Authorization", "Bearer valido"))
               .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }

    @Test
    void passageiroNaoPodeLerListaPrivadaDePassageiros() throws Exception {
        Usuario passageiro = usuario(2, "PASSAGEIRO");
        identidade("passageiro-2", passageiro);
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "passageiro-2")) {
            mvc.perform(get("/turmas/13/passageiros")
                    .header("Authorization", "Bearer valido"))
               .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }

    @Test
    void motoristaNaoPodeConsultarTurmasDeOutroMotorista() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        identidade("motorista-1", dono);
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(get("/turmas/motorista/4")
                    .header("Authorization", "Bearer valido"))
               .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }

    @Test
    void passageiroNaoPodeRegenerarCodigoDeConvite() throws Exception {
        Usuario passageiro = usuario(2, "PASSAGEIRO");
        identidade("passageiro-2", passageiro);
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "passageiro-2")) {
            mvc.perform(put("/turmas/13/regenerar-codigo")
                    .header("Authorization", "Bearer valido"))
               .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
}
