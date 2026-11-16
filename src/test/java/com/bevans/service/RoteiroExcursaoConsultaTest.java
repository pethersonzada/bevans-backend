package com.bevans.service;

import com.bevans.model.ExecucaoExcursao;
import com.bevans.model.EstadoExcursao;
import com.bevans.model.ModoColetaExcursao;
import com.bevans.dto.ExcursaoDTO;
import com.bevans.repository.ExecucaoExcursaoRepository;
import com.bevans.repository.InscricaoExcursaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoteiroExcursaoConsultaTest {

    private final ExcursaoService excursoes = mock(ExcursaoService.class);
    private final InscricaoExcursaoRepository inscricoes = mock(InscricaoExcursaoRepository.class);
    private final ExecucaoExcursaoRepository execucoes = mock(ExecucaoExcursaoRepository.class);
    private final RoteiroExcursaoService service =
            new RoteiroExcursaoService(excursoes, inscricoes, Clock.systemUTC(), execucoes);

    @Test
    void consultaSnapshotDaExecucaoExistenteSemReotimizar() {
        var execucao = mock(ExecucaoExcursao.class);
        when(execucoes.findByExcursaoIdAndSentido(7L, "IDA")).thenReturn(Optional.of(execucao));
        when(execucao.getSnapshotVersao()).thenReturn(1);
        when(execucao.getRoteiroJson()).thenReturn("""
            [{"tipo":"ORIGEM","paradaId":null,"nome":"Origem","latitude":-8.05,
              "longitude":-34.9,"participantes":[]},
             {"tipo":"DESTINO","paradaId":null,"nome":"Destino","latitude":-8.1,
              "longitude":-34.95,"participantes":[]}]
            """);

        var ordem = service.planejamento(7L, "motorista", "IDA", true);

        assertEquals(2, ordem.size());
        assertEquals("ORIGEM", ordem.get(0).tipo());
        assertEquals("DESTINO", ordem.get(1).tipo());
        verify(excursoes).consultar(7L, "motorista");
        verifyNoInteractions(inscricoes);
    }

    @Test
    void naoExpõeSnapshotSemAutorizacaoDoMotorista() {
        when(excursoes.consultar(7L, "estranho")).thenThrow(new AccessDeniedException("Outro motorista"));

        assertThrows(AccessDeniedException.class,
                () -> service.planejamento(7L, "estranho", "IDA", false));

        verifyNoInteractions(execucoes);
    }

    @Test
    void snapshotInvalidoResultaEmErroExplicito() {
        var execucao = mock(ExecucaoExcursao.class);
        when(execucoes.findByExcursaoIdAndSentido(7L, "VOLTA")).thenReturn(Optional.of(execucao));
        when(execucao.getSnapshotVersao()).thenReturn(1);
        when(execucao.getRoteiroJson()).thenReturn("{dados_invalidos}");

        var erro = assertThrows(ResponseStatusException.class,
                () -> service.planejamento(7L, "motorista", "VOLTA", false));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, erro.getStatusCode());
    }
    @Test
    void rascunhoComPartidaPassadaAindaPermiteConsultarPrevia() {
        var excursao = new ExcursaoDTO(
                7L, "Viagem de teste", "Destino", -8.1, -34.95,
                "Origem", -8.05, -34.9,
                Instant.parse("2025-05-01T10:00:00Z"), null,
                12, EstadoExcursao.RASCUNHO, 0L,
                Instant.parse("2025-04-01T10:00:00Z"),
                ModoColetaExcursao.PORTA_A_PORTA, List.of()
        );
        when(excursoes.consultar(7L, "motorista")).thenReturn(excursao);
        when(execucoes.findByExcursaoIdAndSentido(7L, "IDA")).thenReturn(Optional.empty());
        when(inscricoes.findByExcursaoIdAndEstadoOrderByIdAsc(
                7L, com.bevans.model.StatusInscricaoExcursao.CONFIRMADA
        )).thenReturn(List.of());

        var pontos = service.planejamento(7L, "motorista", "IDA", false);

        assertEquals(2, pontos.size());
        assertEquals("Origem", pontos.get(0).nome());
        assertEquals("Destino", pontos.get(1).nome());

        var bloqueio = assertThrows(ResponseStatusException.class,
                () -> service.paraInicio(7L, "motorista", "IDA", false));
        assertEquals(HttpStatus.CONFLICT, bloqueio.getStatusCode());
    }

}
