package com.bevans.security;
import com.bevans.controller.*;
import com.bevans.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={NotificacaoController.class,DispositivoNotificacaoController.class,NotificacaoInscricaoController.class})
@Import({SecurityConfig.class,AccessPolicy.class})
class NotificacaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean NotificacaoService service;@MockBean DispositivoNotificacaoService dispositivos;@MockBean NotificacaoInscricaoService inscricoes;
    @Test void feedExigeFirebaseELeituraNaoAceitaDestinatarioDoBody()throws Exception{
        mvc.perform(get("/notificacoes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/notificacoes").header("X-Inscricao","qualquer")).andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","dono")){
            mvc.perform(put("/notificacoes/9/leitura").header("Authorization","Bearer valido").contentType("application/json").content("{\"usuarioId\":100}")) .andExpect(status().isOk());
        }verify(service).ler("dono",9L);
    }
    @Test void feedWebUsaCredencialIndividualENaoAbreOutrosRecursos()throws Exception{
        mvc.perform(get("/publico/excursoes/inscricao/notificacoes").header("X-Inscricao","individual")).andExpect(status().isOk());
        verify(inscricoes).listar("individual",0,20);
        mvc.perform(get("/publico/excursoes/inscricao/notificacoes/dispositivos")).andExpect(status().isUnauthorized());
    }
}
