package com.bevans.security;

import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.*;
import com.bevans.service.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;

/** Infraestrutura compartilhada dos testes HTTP. Nenhuma conexão externa é feita. */
abstract class BaseAutorizacaoHttpTest {
    @Autowired protected MockMvc mvc;
    @MockBean protected UsuarioRepository usuarios;
    @MockBean protected TurmaRepository turmas;
    @MockBean protected AlunoTurmaRepository vinculos;
    @MockBean protected EnderecoRepository enderecos;
    @MockBean protected PresencaRepository presencas;
    @MockBean protected TurmaService turmaService;
    @MockBean protected RotaService rotaService;
    @MockBean protected ViagemService viagemService;

    protected Usuario usuario(long id, String tipo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setTipo(tipo);
        return u;
    }

    protected void identidade(String uid, Usuario u) {
        when(usuarios.findByFirebaseUid(uid)).thenReturn(Optional.of(u));
    }

    protected Turma turma(long id, Usuario dono) {
        Turma t = new Turma();
        t.setId(id);
        t.setMotorista(dono);
        when(turmas.findById(id)).thenReturn(Optional.of(t));
        return t;
    }

    protected MockedStatic<FirebaseAuth> firebase(String token, String uid) throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getUid()).thenReturn(uid);
        when(auth.verifyIdToken(token)).thenReturn(decoded);
        MockedStatic<FirebaseAuth> scope = mockStatic(FirebaseAuth.class);
        scope.when(FirebaseAuth::getInstance).thenReturn(auth);
        return scope;
    }
}
