package com.bevans.security;
import com.bevans.controller.EmbarqueController;
import com.bevans.repository.RotaPublicaRepository;
import com.bevans.service.EmbarqueService;
import com.bevans.dto.EmbarqueRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=EmbarqueController.class)
@Import({SecurityConfig.class,AccessPolicy.class,RotaPublicaAccess.class})
class EmbarqueHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean EmbarqueService service;
    @MockBean RotaPublicaRepository publicas;
    @Test void identidadeDoPedidoVemDoTokenNaoDoCorpo() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));
        try(var auth=firebase("token","aluno")) {
            mvc.perform(post("/rotas-publicas/9/embarques").header("Authorization","Bearer token").contentType("application/json")
                    .content("{\"alunoId\":999,\"paradaId\":1,\"dataOperacao\":\"2026-12-01\",\"sentido\":\"IDA\"}"))
                    .andExpect(status().isOk());
        }
        verify(service).solicitar(eq(9L),eq(2L),any(EmbarqueRequest.class));
    }
    @Test void passageiroNaoAnalisaNemVePedidosDeRota() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));
        try(var auth=firebase("token","aluno")) {
            mvc.perform(put("/embarques/1/analise").header("Authorization","Bearer token").contentType("application/json").content("{\"status\":\"APROVADO\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/rotas-publicas/9/embarques").header("Authorization","Bearer token").param("data","2026-12-01").param("sentido","IDA"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void minhasECancelamentoUsamSomenteIdentidadeAutenticada() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));
        try(var auth=firebase("token","aluno")) {
            mvc.perform(get("/embarques/minhas").header("Authorization","Bearer token")).andExpect(status().isOk());
            mvc.perform(delete("/embarques/1").header("Authorization","Bearer token")).andExpect(status().isOk());
        }
        verify(service).minhas(2L);verify(service).cancelar(1L,2L);
    }
    @Test void semTokenBloqueiaLeituraEEscrita() throws Exception {
        mvc.perform(get("/embarques/minhas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/rotas-publicas/9/embarques").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
