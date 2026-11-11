package com.bevans.service;

import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.AlunoTurmaRepository;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.ViagemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurmaServiceCoordenadasTest {

    @Mock TurmaRepository turmas;
    @Mock UsuarioRepository usuarios;
    @Mock AlunoTurmaRepository vinculos;
    @Mock ViagemRepository viagens;
    @Mock GeocodingService geocoding;

    TurmaService service;

    @BeforeEach
    void configurar() {
        service = new TurmaService(
                turmas, usuarios, vinculos, viagens, geocoding
        );
    }

    private Turma turmaExistente() {
        Turma turma = new Turma();
        turma.setId(13L);
        turma.setNome("Turma Teste");
        turma.setOrigemNome("Garagem");
        turma.setOrigemLatitude(-8.23);
        turma.setOrigemLongitude(-35.75);
        turma.setDestinoNome("Universidade");
        turma.setDestinoLatitude(-8.30);
        turma.setDestinoLongitude(-35.99);
        return turma;
    }

    @Test
    void atualizaCoordenadasMesmoComNomesIguais() {
        Turma turma = turmaExistente();
        when(turmas.findById(13L)).thenReturn(Optional.of(turma));
        when(turmas.save(any(Turma.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Turma alteracao = new Turma();
        alteracao.setOrigemNome("Garagem");
        alteracao.setOrigemLatitude(-8.24);
        alteracao.setOrigemLongitude(-35.76);
        alteracao.setDestinoNome("Universidade");
        alteracao.setDestinoLatitude(-8.31);
        alteracao.setDestinoLongitude(-35.98);

        Turma resultado = service.atualizarTurma(13L, alteracao);

        assertEquals(-8.24, resultado.getOrigemLatitude());
        assertEquals(-35.76, resultado.getOrigemLongitude());
        assertEquals(-8.31, resultado.getDestinoLatitude());
        assertEquals(-35.98, resultado.getDestinoLongitude());

        verifyNoInteractions(geocoding);
        verify(turmas).save(turma);
    }

    @Test
    void rejeitaCoordenadasIncompletasNaEdicao() {
        Turma turma = turmaExistente();
        when(turmas.findById(13L)).thenReturn(Optional.of(turma));

        Turma alteracao = new Turma();
        alteracao.setDestinoLatitude(-8.31);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.atualizarTurma(13L, alteracao)
        );

        verify(turmas, never()).save(any(Turma.class));
        verifyNoInteractions(geocoding);
    }

    @Test
    void rejeitaCoordenadasInvalidasNaEdicao() {
        Turma turma = turmaExistente();
        when(turmas.findById(13L)).thenReturn(Optional.of(turma));

        Turma alteracao = new Turma();
        alteracao.setDestinoLatitude(0.0);
        alteracao.setDestinoLongitude(0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.atualizarTurma(13L, alteracao)
        );

        verify(turmas, never()).save(any(Turma.class));
    }

    @Test
    void criacaoPriorizaCoordenadasExplicitas() {
        Usuario motorista = new Usuario();
        motorista.setId(1L);
        motorista.setTipo("MOTORISTA");

        when(usuarios.findById(1L))
                .thenReturn(Optional.of(motorista));
        when(turmas.save(any(Turma.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Turma turma = new Turma();
        turma.setNome("Teste Coordenadas");
        turma.setMotorista(motorista);
        turma.setOrigemNome("Garagem");
        turma.setOrigemLatitude(-8.23);
        turma.setOrigemLongitude(-35.75);
        turma.setDestinoNome("Universidade");
        turma.setDestinoLatitude(-8.30);
        turma.setDestinoLongitude(-35.99);

        Turma resultado = service.criarTurma(turma);

        assertEquals(-8.23, resultado.getOrigemLatitude());
        assertEquals(-35.75, resultado.getOrigemLongitude());
        assertEquals(-8.30, resultado.getDestinoLatitude());
        assertEquals(-35.99, resultado.getDestinoLongitude());

        verifyNoInteractions(geocoding);
        verify(turmas).save(turma);
    }

    @Test
    void falhaGeocodificacaoNaoPersisteTurma() {
        Usuario motorista = new Usuario();
        motorista.setId(1L);
        motorista.setTipo("MOTORISTA");

        when(usuarios.findById(1L))
                .thenReturn(Optional.of(motorista));

        when(geocoding.geocodificarEndereco(
                "Destino inexistente", "Pernambuco"
        )).thenThrow(new IllegalArgumentException(
                "Endereço não encontrado"
        ));

        Turma turma = new Turma();
        turma.setNome("Teste Geocodificacao");
        turma.setMotorista(motorista);
        turma.setOrigemNome("Garagem");
        turma.setOrigemLatitude(-8.23);
        turma.setOrigemLongitude(-35.75);
        turma.setDestinoNome("Destino inexistente");

        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> service.criarTurma(turma)
        );

        assertTrue(erro.getMessage().contains(
                "Não foi possível localizar"
        ));

        verify(turmas, never()).save(any(Turma.class));
    }
}
