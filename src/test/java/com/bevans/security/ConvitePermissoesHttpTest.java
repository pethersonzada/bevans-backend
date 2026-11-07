package com.bevans.security;

import com.bevans.controller.TurmaController;
import com.bevans.model.AlunoTurma;
import com.bevans.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TurmaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class ConvitePermissoesHttpTest extends BaseAutorizacaoHttpTest {
    @Test void semTokenNaoPodeSolicitarEntrada() throws Exception {
        mvc.perform(post("/turmas/entrar/2").param("codigo", "ABC123"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(turmaService);
    }
    @Test void usuarioNaoPodeSolicitarEmNomeDeTerceiro() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(post("/turmas/entrar/9").param("codigo", "ABC123")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
    @Test void passageiroPodeSolicitarEmNomeProprio() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(post("/turmas/entrar/2").param("codigo", "ABC123")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isOk());
        }
        verify(turmaService).solicitarEntradaPorCodigo(2L, "ABC123");
    }
    @Test void passageiroNaoPodeAnalisarSolicitacao() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(put("/turmas/analisar/10").param("status", "APROVADO")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
    @Test void outroMotoristaNaoPodeAnalisarSolicitacaoAlheia() throws Exception {
        identidade("motor-1", usuario(1, "MOTORISTA"));
        var vinculo = new AlunoTurma();
        vinculo.setTurma(turma(3, usuario(4, "MOTORISTA")));
        when(vinculos.findById(10L)).thenReturn(Optional.of(vinculo));
        try (var ignored = firebase("valido", "motor-1")) {
            mvc.perform(put("/turmas/analisar/10").param("status", "APROVADO")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
    @Test void donoPodeAprovarSolicitacaoDeSuaTurma() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        identidade("motor-1", dono);
        var vinculo = new AlunoTurma();
        vinculo.setTurma(turma(13, dono));
        when(vinculos.findById(10L)).thenReturn(Optional.of(vinculo));
        try (var ignored = firebase("valido", "motor-1")) {
            mvc.perform(put("/turmas/analisar/10").param("status", "APROVADO")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isOk());
        }
        verify(turmaService).analisarSolicitacao(10L, "APROVADO");
    }
    @Test void donoPodeRejeitarSolicitacaoDeSuaTurma() throws Exception {
        Usuario dono = usuario(1, "MOTORISTA");
        identidade("motor-1", dono);
        var vinculo = new AlunoTurma();
        vinculo.setTurma(turma(13, dono));
        when(vinculos.findById(11L)).thenReturn(Optional.of(vinculo));
        try (var ignored = firebase("valido", "motor-1")) {
            mvc.perform(put("/turmas/analisar/11").param("status", "REJEITADO")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isOk());
        }
        verify(turmaService).analisarSolicitacao(11L, "REJEITADO");
    }
    @Test void outroMotoristaNaoPodeLerPendentes() throws Exception {
        identidade("motor-1", usuario(1, "MOTORISTA"));
        turma(3, usuario(4, "MOTORISTA"));
        try (var ignored = firebase("valido", "motor-1")) {
            mvc.perform(get("/turmas/3/pendentes")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
}
