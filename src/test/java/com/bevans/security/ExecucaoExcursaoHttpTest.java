package com.bevans.security;
import com.bevans.controller.ExecucaoExcursaoController;
import com.bevans.service.ExecucaoExcursaoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=ExecucaoExcursaoController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class ExecucaoExcursaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean ExecucaoExcursaoService service;
    @Test void inicioExigeMotoristaENaoAceitaCredencialDeInscricao() throws Exception {
        mvc.perform(post("/excursoes/1/execucoes").header("X-Inscricao","token")).andExpect(status().isUnauthorized());
        identidade("aluno",usuario(2,"PASSAGEIRO"));try(var ignored=firebase("valido","aluno")){
            mvc.perform(post("/excursoes/1/execucoes").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }verifyNoInteractions(service);
    }
    @Test void finalizacaoUsaUidEContextoDoCaminho() throws Exception {
        identidade("motorista",usuario(1,"MOTORISTA"));try(var ignored=firebase("valido","motorista")){
            mvc.perform(put("/excursoes/1/execucoes/2/finalizacao").header("Authorization","Bearer valido"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        }verify(service).finalizar(1L,2L,"motorista");
    }
}
