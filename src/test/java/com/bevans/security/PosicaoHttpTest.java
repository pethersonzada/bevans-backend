package com.bevans.security;

import com.bevans.controller.PosicaoViagemController;
import com.bevans.controller.PosicaoStreamController;
import com.bevans.dto.PosicaoDTO;
import com.bevans.service.*;
import com.google.firebase.auth.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={PosicaoViagemController.class,PosicaoStreamController.class})
@Import({SecurityConfig.class,AccessPolicy.class})
class PosicaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean PosicaoViagemService gps;
    @MockBean PosicaoStreamService streams;
    @Test void semTokenOuTokenEmQueryNaoLeLocalizacao() throws Exception {
        mvc.perform(get("/viagens/1/localizacao")).andExpect(status().isUnauthorized());
        mvc.perform(get("/viagens/1/localizacao/eventos").param("token","segredo")).andExpect(status().isUnauthorized());
        verifyNoInteractions(gps,streams);
    }
    @Test void consultaUsaIdentidadeDoTokenEImpedeCache() throws Exception {
        when(gps.consultar(1L,"uid")).thenReturn(new PosicaoDTO(1L,"SEM_LOCALIZACAO",null,null,null,null,null,null,10));
        try(var ignored=firebase("valido","uid")){
            mvc.perform(get("/viagens/1/localizacao").param("usuarioId","99").header("Authorization","Bearer valido"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.estado").value("SEM_LOCALIZACAO"));
        }
        verify(gps).consultar(1L,"uid");
    }
    @Test void naoAutorizadoRecebe403SemGps() throws Exception {
        when(gps.consultar(1L,"uid")).thenThrow(new AccessDeniedException("Sem vínculo"));
        try(var ignored=firebase("valido","uid")){
            mvc.perform(get("/viagens/1/localizacao").header("Authorization","Bearer valido"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.latitude").doesNotExist());
        }
    }
    @Test void sseUsaExpiracaoVerificadaPeloFirebaseESemBufferDoProxy() throws Exception {
        FirebaseAuth auth=mock(FirebaseAuth.class);FirebaseToken token=mock(FirebaseToken.class);
        Instant expira=Instant.now().plusSeconds(600);when(token.getUid()).thenReturn("uid");when(token.getClaims()).thenReturn(Map.of("exp",expira.getEpochSecond()));
        when(auth.verifyIdToken("valido")).thenReturn(token);
        when(streams.abrir(eq(1L),eq("uid"),eq(Instant.ofEpochSecond(expira.getEpochSecond())))).thenReturn(new SseEmitter(1000L));
        try(var scope=mockStatic(FirebaseAuth.class)){
            scope.when(FirebaseAuth::getInstance).thenReturn(auth);
            mvc.perform(get("/viagens/1/localizacao/eventos").header("Authorization","Bearer valido"))
                .andExpect(request().asyncStarted()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Accel-Buffering","no"));
        }
    }
}
