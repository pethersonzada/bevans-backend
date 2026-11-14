package com.bevans.security;
import com.bevans.controller.RoteiroExcursaoController;
import com.bevans.service.RoteiroExcursaoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=RoteiroExcursaoController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class RoteiroExcursaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean RoteiroExcursaoService service;
    @Test void planejamentoExigeMotoristaENaoPodeSerAcessadoPorConvite() throws Exception {
        mvc.perform(post("/excursoes/1/planejamento").header("X-Convite","convite")).andExpect(status().isUnauthorized());
        identidade("aluno",usuario(2,"PASSAGEIRO"));try(var ignored=firebase("valido","aluno")){
            mvc.perform(post("/excursoes/1/planejamento").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }verifyNoInteractions(service);
    }
    @Test void identidadeEParametroDeOtimizacaoSaoExplicitos() throws Exception {
        identidade("motorista",usuario(1,"MOTORISTA"));try(var ignored=firebase("valido","motorista")){
            mvc.perform(post("/excursoes/1/planejamento").param("otimizar","true").param("sentido","VOLTA").header("Authorization","Bearer valido"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        }verify(service).planejamento(1L,"motorista","VOLTA",true);
    }
}
