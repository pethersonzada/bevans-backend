package com.bevans.security;
import com.bevans.controller.RotaPublicaController;
import com.bevans.model.*;
import com.bevans.repository.RotaPublicaRepository;
import com.bevans.service.RotaPublicaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=RotaPublicaController.class)
@Import({SecurityConfig.class,AccessPolicy.class,RotaPublicaAccess.class})
class RotaPublicaHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean RotaPublicaRepository publicas;
    @MockBean RotaPublicaService service;
    private void rota(boolean publicada) {
        RotaPublica r=new RotaPublica();r.setId(9L);r.setTurma(turma(7,usuario(1,"MOTORISTA")));r.setPublicada(publicada);r.setAtiva(true);
        when(publicas.findById(9L)).thenReturn(Optional.of(r));
    }
    @Test void passageiroNaoVeRotaPrivadaNemEditaParadas() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));rota(false);
        try(var auth=firebase("token","aluno")) {
            mvc.perform(get("/rotas-publicas/9").header("Authorization","Bearer token")).andExpect(status().isForbidden());
            mvc.perform(put("/rotas-publicas/9/paradas").header("Authorization","Bearer token").contentType("application/json").content("[]"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void motoristaAlheioNaoPublica() throws Exception {
        identidade("outro",usuario(3,"MOTORISTA"));rota(false);
        try(var auth=firebase("token","outro")) {
            mvc.perform(put("/rotas-publicas/9/publicacao").header("Authorization","Bearer token").contentType("application/json").content("{\"valor\":true}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void pedidoNaoAceitaIdentidadeArbitrariaDoCorpo() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));
        try(var auth=firebase("token","aluno")) {
            mvc.perform(post("/rotas-publicas/9/solicitacoes").header("Authorization","Bearer token").contentType("application/json").content("{\"alunoId\":999}"))
                    .andExpect(status().isOk());
        }
        verify(service).solicitar(9L,2L);
    }
    @Test void semTokenNaoVeNemEditaRotas() throws Exception {
        mvc.perform(get("/rotas-publicas/minhas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/rotas-publicas").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
