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
        turma(13, usuario(1, "MOTORISTA"));
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

    @Test
    void localizacaoFuncionaComViagemAtivaPersistida() throws Exception {
        var motorista = usuario(1, "MOTORISTA");
        identidade("motor-localizacao", motorista);
        turma(913, motorista);

        when(viagemService.rotaAtiva(913L)).thenReturn(true);

        try (var ignored = firebase("token-localizacao", "motor-localizacao")) {
            mvc.perform(post("/rota/localizacao-van")
                    .param("turmaId", "913")
                    .param("latitude", "-8.23")
                    .param("longitude", "-35.75")
                    .header("Authorization", "Bearer token-localizacao"))
                .andExpect(status().isOk());

            mvc.perform(get("/rota/localizacao-van")
                    .param("turmaId", "913")
                    .header("Authorization", "Bearer token-localizacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(-8.23))
                .andExpect(jsonPath("$.longitude").value(-35.75));
        }

        verify(viagemService, times(2)).rotaAtiva(913L);
    }

    @Test
    void localizacaoRejeitaCoordenadasInvalidas() throws Exception {
        var motorista = usuario(1, "MOTORISTA");
        identidade("motor-coordenadas", motorista);
        turma(914, motorista);

        when(viagemService.rotaAtiva(914L)).thenReturn(true);

        try (var ignored = firebase("token-coordenadas", "motor-coordenadas")) {
            mvc.perform(post("/rota/localizacao-van")
                    .param("turmaId", "914")
                    .param("latitude", "100")
                    .param("longitude", "-35.75")
                    .header("Authorization", "Bearer token-coordenadas"))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void localizacaoBloqueadaSemViagemAtiva() throws Exception {
        var motorista = usuario(1, "MOTORISTA");
        identidade("motor-inativo", motorista);
        turma(915, motorista);

        when(viagemService.rotaAtiva(915L)).thenReturn(false);

        try (var ignored = firebase("token-inativo", "motor-inativo")) {
            mvc.perform(post("/rota/localizacao-van")
                    .param("turmaId", "915")
                    .param("latitude", "-8.23")
                    .param("longitude", "-35.75")
                    .header("Authorization", "Bearer token-inativo"))
                .andExpect(status().isBadRequest());
        }
    }

}
