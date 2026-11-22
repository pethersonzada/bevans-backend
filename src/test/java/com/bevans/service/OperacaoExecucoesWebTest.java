package com.bevans.service;

import com.bevans.model.*;
import com.bevans.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperacaoExecucoesWebTest {
    private static final String TOKEN = "A".repeat(43);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC);
    @Mock RegistroOperacionalRepository registros;
    @Mock InscricaoExcursaoRepository inscricoes;
    @Mock CredencialInscricaoExcursaoRepository credenciais;
    @Mock CredencialInscricaoExcursao credencial;
    @Mock InscricaoExcursao inscricao;
    @Mock Excursao excursao;
    OperacaoService service;

    @BeforeEach void criarServico(){
        service = new OperacaoService(null,registros,null,null,null,null,null,null,null,
                null,null,null,null,inscricoes,credenciais,null,null,clock,null);
    }

    private void credencialValida(){
        when(credenciais.findByTokenHash(TokensExcursao.hash(TOKEN))).thenReturn(Optional.of(credencial));
        when(credencial.getExpiraEm()).thenReturn(clock.instant().plusSeconds(120));
        when(credencial.getId()).thenReturn(5L);
    }

    private void inscricaoConfirmada(){
        credencialValida();
        when(inscricoes.findByCredencialId(5L)).thenReturn(Optional.of(inscricao));
        when(inscricao.getEstado()).thenReturn(StatusInscricaoExcursao.CONFIRMADA);
        when(inscricao.getExcursao()).thenReturn(excursao);
        when(excursao.getEstado()).thenReturn(EstadoExcursao.PUBLICADA);
        when(excursao.getId()).thenReturn(10L);
        when(credencial.getExcursao()).thenReturn(excursao);
        when(inscricao.getId()).thenReturn(7L);
    }

    private RegistroOperacional registro(Long id, Excursao vinculada){
        ExecucaoExcursao execucao=mock(ExecucaoExcursao.class);
        when(execucao.getExcursao()).thenReturn(vinculada);
        if(id!=null){
            when(execucao.getId()).thenReturn(id);
            when(execucao.getSentido()).thenReturn("IDA");
            when(execucao.getEstado()).thenReturn(EstadoExecucaoExcursao.EM_ANDAMENTO);
            when(execucao.getIniciadaEm()).thenReturn(clock.instant());
        }
        OperacaoViagem operacao=new OperacaoViagem();
        operacao.tipo=TipoOperacao.EXCURSAO;
        operacao.execucao=execucao;
        RegistroOperacional r=new RegistroOperacional();
        r.operacao=operacao;
        return r;
    }

    @Test void credencialAusenteOuInvalidaNaoConsultaRegistros(){
        assertEquals(404,assertThrows(ResponseStatusException.class,
                ()->service.execucoesWeb(null)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,
                ()->service.execucoesWeb("valor-curto")).getStatusCode().value());
        verifyNoInteractions(registros,inscricoes,credenciais);
    }

    @Test void credencialExpiradaNaoConsultaInscricao(){
        when(credenciais.findByTokenHash(TokensExcursao.hash(TOKEN))).thenReturn(Optional.of(credencial));
        when(credencial.getExpiraEm()).thenReturn(clock.instant());
        assertEquals(404,assertThrows(ResponseStatusException.class,
                ()->service.execucoesWeb(TOKEN)).getStatusCode().value());
        verifyNoInteractions(inscricoes,registros);
    }

    @Test void inscricaoNaoConfirmadaNaoDescobreExecucoes(){
        credencialValida();
        when(inscricoes.findByCredencialId(5L)).thenReturn(Optional.of(inscricao));
        when(inscricao.getEstado()).thenReturn(StatusInscricaoExcursao.SOLICITADA);
        assertEquals(404,assertThrows(ResponseStatusException.class,
                ()->service.execucoesWeb(TOKEN)).getStatusCode().value());
        verifyNoInteractions(registros);
    }

    @Test void consultaSomenteAExecucaoPresenteNoRegistroDaPropriaInscricao(){
        inscricaoConfirmada();
        Excursao outra=mock(Excursao.class);
        when(outra.getId()).thenReturn(11L);
        RegistroOperacional minha=registro(21L,excursao);
        RegistroOperacional alheia=registro(null,outra);
        when(registros.findByInscricaoIdOrderByIdAsc(7L)).thenReturn(List.of(minha,alheia));
        var resposta=service.execucoesWeb(TOKEN);
        assertEquals(1,resposta.size());
        assertEquals(21L,resposta.getFirst().id());
        assertEquals("IDA",resposta.getFirst().sentido());
        assertEquals(EstadoExecucaoExcursao.EM_ANDAMENTO,resposta.getFirst().estado());
        verify(registros).findByInscricaoIdOrderByIdAsc(7L);
    }

    @Test void semViagemIniciadaDevolveListaVaziaSemIdentificarTerceiros(){
        inscricaoConfirmada();
        when(registros.findByInscricaoIdOrderByIdAsc(7L)).thenReturn(List.of());
        assertTrue(service.execucoesWeb(TOKEN).isEmpty());
    }
}
