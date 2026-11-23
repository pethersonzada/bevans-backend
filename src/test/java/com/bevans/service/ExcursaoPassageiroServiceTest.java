package com.bevans.service;

import com.bevans.model.*;
import com.bevans.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExcursaoPassageiroServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final InscricaoExcursaoRepository inscricoes =
        mock(InscricaoExcursaoRepository.class);
    private final CredencialInscricaoExcursaoRepository credenciais =
        mock(CredencialInscricaoExcursaoRepository.class);

    private final Clock clock = Clock.fixed(
        Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);

    private final ExcursaoPassageiroService service =
        new ExcursaoPassageiroService(
            usuarios, inscricoes, credenciais, clock);

    private final String token = TokensExcursao.gerar();

    private Usuario passageiro;
    private CredencialInscricaoExcursao credencial;
    private InscricaoExcursao inscricao;

    @BeforeEach
    void preparar() {
        passageiro = mock(Usuario.class);
        credencial = mock(CredencialInscricaoExcursao.class);
        inscricao = mock(InscricaoExcursao.class);

        when(passageiro.getId()).thenReturn(10L);
        when(passageiro.getTipo()).thenReturn("PASSAGEIRO");
        when(usuarios.findByFirebaseUid("uid-10"))
            .thenReturn(Optional.of(passageiro));

        when(credencial.getId()).thenReturn(20L);
        when(credencial.getExpiraEm())
            .thenReturn(clock.instant().plusSeconds(3600));

        when(credenciais.findByTokenHash(TokensExcursao.hash(token)))
            .thenReturn(Optional.of(credencial));

        when(inscricoes.lockByCredencialId(20L))
            .thenReturn(Optional.of(inscricao));
    }

    @Test
    void vinculaInscricaoDisponivel() {
        service.vincular("uid-10", token);

        verify(inscricao).vincularPassageiro(passageiro);
        verify(inscricoes).save(inscricao);
    }

    @Test
    void repetirVinculoDaMesmaContaEhIdempotente() {
        when(inscricao.getPassageiro()).thenReturn(passageiro);

        service.vincular("uid-10", token);

        verify(inscricao, never()).vincularPassageiro(any());
        verify(inscricoes, never()).save(any());
    }

    @Test
    void impedeOutraContaDeReivindicarInscricao() {
        Usuario outro = mock(Usuario.class);
        when(outro.getId()).thenReturn(99L);
        when(inscricao.getPassageiro()).thenReturn(outro);

        var erro = assertThrows(ResponseStatusException.class,
            () -> service.vincular("uid-10", token));

        assertEquals(HttpStatus.CONFLICT, erro.getStatusCode());
        verify(inscricao, never()).vincularPassageiro(any());
    }

    @Test
    void rejeitaCredencialInvalida() {
        var erro = assertThrows(ResponseStatusException.class,
            () -> service.vincular("uid-10", "invalida"));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        verifyNoInteractions(inscricoes);
    }

    @Test
    void rejeitaCredencialExpirada() {
        when(credencial.getExpiraEm())
            .thenReturn(clock.instant().minusSeconds(1));

        var erro = assertThrows(ResponseStatusException.class,
            () -> service.vincular("uid-10", token));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
        verifyNoInteractions(inscricoes);
    }

    @Test
    void rejeitaPerfilDiferenteDePassageiro() {
        when(passageiro.getTipo()).thenReturn("MOTORISTA");

        var erro = assertThrows(ResponseStatusException.class,
            () -> service.vincular("uid-10", token));

        assertEquals(HttpStatus.FORBIDDEN, erro.getStatusCode());
        verifyNoInteractions(credenciais, inscricoes);
    }
}
