package com.bevans.security;

import com.bevans.controller.SolicitacaoController;
import com.bevans.dto.SolicitacaoVagaDTO;
import com.bevans.model.AlunoTurma;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=SolicitacaoController.class)
@Import({SecurityConfig.class,AccessPolicy.class})
class SolicitacaoHttpTest extends BaseAutorizacaoHttpTest {
    @Test void listaSomenteDoUsuarioAutenticadoSemDadosPrivados() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));
        when(turmaService.minhasSolicitacoes(2L)).thenReturn(List.of(new SolicitacaoVagaDTO(11L,7L,"Faculdade","PENDENTE")));
        try(var auth=firebase("token","aluno")) {
            mvc.perform(get("/turmas/solicitacoes").header("Authorization","Bearer token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(11))
                    .andExpect(jsonPath("$[0].aluno").doesNotExist()).andExpect(jsonPath("$[0].motorista").doesNotExist());
        }
        verify(turmaService).minhasSolicitacoes(2L);
    }
    @Test void somenteTitularCancela() throws Exception {
        var aluno=usuario(2,"PASSAGEIRO");identidade("aluno",aluno);
        AlunoTurma v=new AlunoTurma();v.setAluno(aluno);when(vinculos.findById(11L)).thenReturn(Optional.of(v));
        try(var auth=firebase("token","aluno")) {
            mvc.perform(delete("/turmas/solicitacoes/11").header("Authorization","Bearer token")).andExpect(status().isNoContent());
        }
        verify(turmaService).cancelarSolicitacao(11L,2L);
    }
    @Test void idDeOutroPassageiroBloqueado() throws Exception {
        identidade("outro",usuario(3,"PASSAGEIRO"));
        AlunoTurma v=new AlunoTurma();v.setAluno(usuario(2,"PASSAGEIRO"));when(vinculos.findById(11L)).thenReturn(Optional.of(v));
        try(var auth=firebase("token","outro")) {
            mvc.perform(delete("/turmas/solicitacoes/11").header("Authorization","Bearer token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
    @Test void sessaoAusenteBloqueada() throws Exception {
        mvc.perform(get("/turmas/solicitacoes")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/turmas/solicitacoes/11")).andExpect(status().isUnauthorized());
        verifyNoInteractions(turmaService);
    }
}
