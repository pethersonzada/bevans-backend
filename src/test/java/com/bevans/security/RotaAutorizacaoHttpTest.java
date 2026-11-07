package com.bevans.security;

import com.bevans.controller.RotaController;
import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.AlunoTurmaRepository;
import com.bevans.repository.EnderecoRepository;
import com.bevans.repository.PresencaRepository;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.service.RotaService;
import com.bevans.service.ViagemService;
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

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Testes HTTP de autorização de rotas. Não acessa Firebase externo nem banco. */
@WebMvcTest(controllers = RotaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class RotaAutorizacaoHttpTest {
    @Autowired MockMvc mvc;

    @MockBean PresencaRepository presencas;
    @MockBean UsuarioRepository usuarios;
    @MockBean EnderecoRepository enderecos;
    @MockBean AlunoTurmaRepository vinculos;
    @MockBean TurmaRepository turmas;
    @MockBean RotaService rotaService;
    @MockBean ViagemService viagemService;

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
    void semTokenNaoPodeIniciarRota() throws Exception {
        mvc.perform(post("/rota/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"turmaId\":13,\"sentido\":\"IDA\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(viagemService);
    }

    @Test
    void motoristaDonoPodeIniciarRota() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        identidade("motorista-1", dono);
        turma(13, dono);
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer valido")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"turmaId\":13,\"sentido\":\"IDA\"}"))
                .andExpect(status().isOk());
        }
        verify(viagemService).iniciarRota(13L, "IDA");
    }

    @Test
    void motoristaNaoPodeIniciarRotaDeOutro() throws Exception {
        Usuario motorista = usuario(1, "MOTORISTA");
        identidade("motorista-1", motorista);
        turma(3, usuario(4, "MOTORISTA"));
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer valido")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"turmaId\":3,\"sentido\":\"IDA\"}"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(viagemService);
    }

    @Test
    void passageiroNaoPodeIniciarRota() throws Exception {
        identidade("passageiro-2", usuario(2, "PASSAGEIRO"));
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "passageiro-2")) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer valido")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"turmaId\":13,\"sentido\":\"IDA\"}"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(viagemService);
    }

    @Test
    void motoristaNaoPodeEncerrarRotaDeOutro() throws Exception {
        identidade("motorista-1", usuario(1, "MOTORISTA"));
        turma(3, usuario(4, "MOTORISTA"));
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(post("/rota/encerrar")
                    .header("Authorization", "Bearer valido")
                    .param("turmaId", "3"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(viagemService);
    }

    @Test
    void motoristaDonoPodeEncerrarRota() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        identidade("motorista-1", dono);
        turma(13, dono);
        try (MockedStatic<FirebaseAuth> scope = firebase("valido", "motorista-1")) {
            mvc.perform(post("/rota/encerrar")
                    .header("Authorization", "Bearer valido")
                    .param("turmaId", "13"))
                .andExpect(status().isOk());
        }
        verify(viagemService).encerrarRota(13L);
    }
}
