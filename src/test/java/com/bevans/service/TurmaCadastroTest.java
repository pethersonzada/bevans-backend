package com.bevans.service;

import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurmaCadastroTest {
    @Mock TurmaRepository turmas;
    @Mock UsuarioRepository usuarios;
    @Mock AlunoTurmaRepository vinculos;
    @Mock ViagemRepository viagens;
    @Mock GeocodingService geocoding;
    TurmaService service;

    @BeforeEach void setup() {
        service = new TurmaService(turmas, usuarios, vinculos, viagens, geocoding);
    }

    Turma rascunho() {
        Usuario motorista = new Usuario();
        motorista.setId(1L);
        motorista.setTipo("MOTORISTA");
        when(usuarios.findById(1L)).thenReturn(Optional.of(motorista));
        Turma turma = new Turma();
        turma.setNome("  Faculdade  ");
        turma.setTurno("NOITE");
        turma.setMotorista(motorista);
        return turma;
    }

    @Test void criaRascunhoParaAbrirMapaSemInventarCoordenadas() {
        Turma turma = rascunho();
        turma.setId(99L);
        turma.setExcluido(true);
        when(turmas.save(turma)).thenReturn(turma);
        assertSame(turma, service.criarTurma(turma));
        assertNull(turma.getOrigemLatitude());
        assertNull(turma.getDestinoLatitude());
        assertEquals("Faculdade", turma.getNome());
        assertNotNull(turma.getCodigoConvite());
        assertNull(turma.getId());
        assertFalse(turma.isExcluido());
        verifyNoInteractions(geocoding);
    }

    @Test void nomeSemPinNaoGeocodificaNemSalva() {
        Turma turma = rascunho();
        turma.setDestinoNome("Rua sem número");
        assertThrows(IllegalArgumentException.class, () -> service.criarTurma(turma));
        verifyNoInteractions(geocoding);
        verify(turmas, never()).save(any());
    }

    @Test void coordenadaIncompletaNaoSalva() {
        Turma turma = rascunho();
        turma.setOrigemLatitude(-8.0);
        assertThrows(IllegalArgumentException.class, () -> service.criarTurma(turma));
        verify(turmas, never()).save(any());
    }

    @Test void salvaOrigemEDestinoSeparadamenteEPreservaParNaoAlterado() {
        Turma turma = new Turma();
        when(turmas.findById(7L)).thenReturn(Optional.of(turma));
        when(turmas.save(turma)).thenReturn(turma);
        Turma origem = new Turma();
        origem.setOrigemNome("Garagem");
        origem.setOrigemLatitude(-8.0);
        origem.setOrigemLongitude(-35.0);
        service.atualizarTurma(7L, origem);
        Turma destino = new Turma();
        destino.setDestinoNome("Faculdade");
        destino.setDestinoLatitude(-8.1);
        destino.setDestinoLongitude(-35.1);
        service.atualizarTurma(7L, destino);
        assertEquals(-8.0, turma.getOrigemLatitude());
        assertEquals(-35.0, turma.getOrigemLongitude());
        assertEquals(-8.1, turma.getDestinoLatitude());
        assertEquals(-35.1, turma.getDestinoLongitude());
        verifyNoInteractions(geocoding);
    }

    @Test void mesmoNomeComNovoPinPersisteCoordenadas() {
        Turma turma = new Turma();
        turma.setOrigemNome("Garagem");
        when(turmas.findById(7L)).thenReturn(Optional.of(turma));
        Turma patch = new Turma();
        patch.setOrigemNome("Garagem");
        patch.setOrigemLatitude(-8.2);
        patch.setOrigemLongitude(-35.2);
        service.atualizarTurma(7L, patch);
        assertEquals(-8.2, turma.getOrigemLatitude());
        verify(turmas).save(turma);
        verifyNoInteractions(geocoding);
    }
}
