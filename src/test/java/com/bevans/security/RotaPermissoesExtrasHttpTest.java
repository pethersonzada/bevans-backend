package com.bevans.security;

import com.bevans.controller.RotaController;
import com.bevans.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = RotaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class RotaPermissoesExtrasHttpTest extends BaseAutorizacaoHttpTest {
    @Test void semTokenNaoPodeOtimizar() throws Exception {
        mvc.perform(get("/rota/otimizar").param("turmaId", "13").param("sentido", "IDA"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(rotaService);
    }
    @Test void motoristaAlheioNaoPodeOtimizar() throws Exception {
        identidade("motor-1", usuario(1, "MOTORISTA"));
        turma(3, usuario(4, "MOTORISTA"));
        try (var ignored = firebase("valido", "motor-1")) {
            mvc.perform(get("/rota/otimizar").param("turmaId", "3").param("sentido", "IDA")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(rotaService);
    }
    @Test void passageiroNaoPodeOtimizar() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(get("/rota/otimizar").param("turmaId", "13").param("sentido", "IDA")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(rotaService);
    }
    @Test void motoristaAlheioNaoPodeAtualizarLocalizacao() throws Exception {
        identidade("motor-1", usuario(1, "MOTORISTA"));
        turma(3, usuario(4, "MOTORISTA"));
        try (var ignored = firebase("valido", "motor-1")) {
            mvc.perform(post("/rota/localizacao-van").param("turmaId", "3")
                .param("latitude", "-8.0").param("longitude", "-35.0")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
    }
    @Test void passageiroNaoAprovadoNaoPodeLerLocalizacao() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(get("/rota/localizacao-van").param("turmaId", "13")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
    }
    @Test void passageiroAprovadoPodeConsultarStatusDaRota() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        when(vinculos.existsByTurmaIdAndAlunoIdAndStatus(13L, 2L, "APROVADO"))
            .thenReturn(true);
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(get("/rota/status-atual").param("turmaId", "13")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isOk());
        }
        verify(viagemService).verificarStatusAtual(13L);
    }
    @Test void passageiroPendenteNaoPodeConsultarStatusDaRota() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(get("/rota/status-atual").param("turmaId", "13")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(viagemService);
    }
    @Test void usuarioNaoPodeConfirmarPresencaDeOutro() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(post("/rota/confirmar").param("usuarioId", "9")
                .param("turmaId", "13").param("status", "NAO_VOU")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(presencas);
    }
    @Test void passageiroNaoAprovadoNaoPodeConfirmarPresenca() throws Exception {
        identidade("pass-2", usuario(2, "PASSAGEIRO"));
        try (var ignored = firebase("valido", "pass-2")) {
            mvc.perform(post("/rota/confirmar").param("usuarioId", "2")
                .param("turmaId", "13").param("status", "NAO_VOU")
                .header("Authorization", "Bearer valido"))
                .andExpect(status().isForbidden());
        }
    }
}
