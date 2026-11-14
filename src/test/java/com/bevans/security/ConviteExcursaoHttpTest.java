package com.bevans.security;
import com.bevans.controller.*;
import com.bevans.dto.*;
import com.bevans.model.ModoColetaExcursao;
import com.bevans.service.ConviteExcursaoService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={ConviteExcursaoController.class,ConviteExcursaoPublicoController.class})
@Import({SecurityConfig.class,AccessPolicy.class})
class ConviteExcursaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean ConviteExcursaoService service;
    @Test void convitePublicoNaoExigeFirebaseENaoIncluiDadosPrivados() throws Exception {
        when(service.consultar("token-teste")).thenReturn(new ExcursaoConvitePublicoDTO("Evento","Destino",-8,-35,Instant.parse("2030-01-01T12:00:00Z"),null,ModoColetaExcursao.PORTA_A_PORTA,List.of()));
        mvc.perform(get("/publico/excursoes/convite").header("X-Convite","token-teste"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.titulo").value("Evento")).andExpect(jsonPath("$.organizador").doesNotExist())
                .andExpect(jsonPath("$.origemLatitude").doesNotExist()).andExpect(jsonPath("$.inscritos").doesNotExist());
    }
    @Test void tokenNaoPodeVirPelaUrlEOutrosEndpointsContinuamPrivados() throws Exception {
        when(service.consultar(null)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Convite indisponível."));
        mvc.perform(get("/publico/excursoes/convite").param("token","qualquer")).andExpect(status().isNotFound());
        mvc.perform(get("/excursoes/1/convites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/publico/excursoes/participantes")).andExpect(status().isUnauthorized());
    }
    @Test void administracaoExigeMotoristaEUidDoToken() throws Exception {
        identidade("aluno",usuario(2,"PASSAGEIRO"));try(var ignored=firebase("valido","aluno")){
            mvc.perform(delete("/excursoes/1/convites/2").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }
        identidade("motorista",usuario(1,"MOTORISTA"));try(var ignored=firebase("valido","motorista")){
            mvc.perform(delete("/excursoes/1/convites/2").header("Authorization","Bearer valido")).andExpect(status().isNoContent());
        }
        verify(service).revogar(1L,2L,"motorista");
    }
}
