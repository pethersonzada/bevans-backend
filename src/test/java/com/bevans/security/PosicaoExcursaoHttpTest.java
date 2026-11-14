package com.bevans.security;
import com.bevans.controller.PosicaoExcursaoController;
import com.bevans.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(PosicaoExcursaoController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class PosicaoExcursaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean PosicaoExcursaoService service;@MockBean PosicaoStreamService streams;
    @Test void conviteNaoPermiteEnviarLocalizacaoEOrganizadorVemDoFirebase()throws Exception{
        mvc.perform(put("/execucoes-excursao/2/localizacao").header("X-Convite","convite").contentType("application/json").content("{}")) .andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","organizador")){
            mvc.perform(put("/execucoes-excursao/2/localizacao").header("Authorization","Bearer valido").contentType("application/json").content("{\"amostraId\":\"a\"}")) .andExpect(status().isOk());
        }verify(service).enviar(eq(2L),eq("organizador"),any());
    }
    @Test void consultaParticipanteEncaminhaSomenteInscricaoEEnviarPublicamenteNaoEPermitido()throws Exception{
        when(service.consultarParticipante(2L,null)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(get("/publico/excursoes/operacao/2/localizacao").header("X-Convite","convite")).andExpect(status().isNotFound());
        verify(service).consultarParticipante(2L,null);
        mvc.perform(get("/publico/excursoes/operacao/2/localizacao").header("X-Inscricao","individual")).andExpect(status().isOk());
        verify(service).consultarParticipante(2L,"individual");
        mvc.perform(put("/publico/excursoes/operacao/2/localizacao").contentType("application/json").content("{}")) .andExpect(status().isUnauthorized());
    }
}
