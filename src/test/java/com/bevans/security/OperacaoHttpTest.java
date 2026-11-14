package com.bevans.security;
import com.bevans.controller.*;
import com.bevans.service.OperacaoService;
import com.bevans.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={OperacaoController.class,OperacaoExcursaoPublicoController.class}) @Import({SecurityConfig.class,AccessPolicy.class})
class OperacaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean OperacaoService service;
    @Test void rotaAutenticadaNaoAceitaTokenDeConviteEAtorVemDoFirebase() throws Exception {
        mvc.perform(get("/operacoes/VIAGEM/1").header("X-Convite","token")).andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","aluno")){
            mvc.perform(put("/operacoes/VIAGEM/1/minha/estado").header("Authorization","Bearer valido").contentType("application/json").content("{\"estado\":\"EMBARCADO\",\"versao\":0,\"alunoId\":99}"))
                    .andExpect(status().isOk());
        }verify(service).alterarMinha(eq(1L),eq("aluno"),argThat(d->d.estado()==EstadoOperacional.EMBARCADO));
    }
    @Test void webUsaSoCredencialIndividualEIdDaExecucao() throws Exception {
        mvc.perform(get("/publico/excursoes/operacao/5/minha").header("X-Inscricao","credencial")).andExpect(status().isOk());
        verify(service).minhaWeb(5L,"credencial");mvc.perform(get("/publico/excursoes/operacao/5/passageiros")).andExpect(status().isUnauthorized());
    }
}
