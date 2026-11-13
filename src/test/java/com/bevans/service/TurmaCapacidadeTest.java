package com.bevans.service;

import com.bevans.exception.CapacidadeException;
import com.bevans.model.*;
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
class TurmaCapacidadeTest {
    @Mock TurmaRepository turmas;
    @Mock UsuarioRepository usuarios;
    @Mock AlunoTurmaRepository vinculos;
    @Mock ViagemRepository viagens;
    @Mock GeocodingService geocoding;
    @Mock jakarta.persistence.EntityManager entityManager;
    TurmaService service;
    @BeforeEach void setup() { service = new TurmaService(turmas,usuarios,vinculos,viagens,geocoding,entityManager); }

    @Test void naoReduzAbaixoDeAprovados() {
        Turma turma = new Turma(); turma.setCapacidade(12);
        when(turmas.lockById(7L)).thenReturn(Optional.of(turma));
        when(vinculos.countByTurmaIdAndStatus(7L,"APROVADO")).thenReturn(10L);
        Turma patch = new Turma(); patch.setCapacidade(9);
        assertThrows(CapacidadeException.class,()->service.atualizarTurma(7L,patch));
        assertEquals(12,turma.getCapacidade()); verify(turmas,never()).save(any());
    }
    @Test void capacidadePositivaEIndependenteDaOutraTurma() {
        Turma turma = new Turma(); turma.setCapacidade(12);
        when(turmas.lockById(7L)).thenReturn(Optional.of(turma));
        Turma patch = new Turma(); patch.setCapacidade(15);
        service.atualizarTurma(7L,patch);
        assertEquals(15,turma.getCapacidade()); verify(turmas).save(turma);
        verify(turmas,never()).lockById(8L);
    }
    @Test void zeroRejeitado() {
        when(turmas.lockById(7L)).thenReturn(Optional.of(new Turma()));
        Turma patch = new Turma(); patch.setCapacidade(0);
        assertThrows(IllegalArgumentException.class,()->service.atualizarTurma(7L,patch));
    }
    @Test void turmaLotadaNaoAprova() {
        when(vinculos.turmaIdDoVinculo(3L)).thenReturn(Optional.of(7L));
        when(turmas.lockById(7L)).thenReturn(Optional.of(new Turma()));
        when(vinculos.statusAtual(3L)).thenReturn("PENDENTE");
        when(turmas.capacidadeAtual(7L)).thenReturn(1);
        when(vinculos.countByTurmaIdAndStatus(7L,"APROVADO")).thenReturn(1L);
        assertThrows(CapacidadeException.class,()->service.analisarSolicitacao(3L,"APROVADO"));
        verify(vinculos,never()).save(any());
    }
    @Test void capacidadeLegadaNulaNaoInventaVagas() {
        when(turmas.findById(7L)).thenReturn(Optional.of(new Turma()));
        when(turmas.capacidadeAtual(7L)).thenReturn(null);
        when(vinculos.countByTurmaIdAndStatus(7L,"APROVADO")).thenReturn(8L);
        var vagas=service.consultarVagas(7L);
        assertFalse(vagas.configurada()); assertNull(vagas.disponiveis()); assertEquals(8,vagas.ocupados());
    }

    private AlunoTurma prepararCancelamento(String status) {
        when(vinculos.turmaIdDoVinculo(3L)).thenReturn(Optional.of(7L));
        when(turmas.lockById(7L)).thenReturn(Optional.of(new Turma()));
        Usuario aluno=new Usuario();aluno.setId(2L);
        AlunoTurma v=new AlunoTurma();v.setAluno(aluno);v.setStatus(status);
        when(vinculos.findById(3L)).thenReturn(Optional.of(v));return v;
    }
    @Test void cancelamentoRepetidoEIdempotente() {
        var v=prepararCancelamento("PENDENTE");
        service.cancelarSolicitacao(3L,2L);service.cancelarSolicitacao(3L,2L);
        assertEquals("CANCELADO",v.getStatus());verify(vinculos,times(1)).save(v);
    }
    @Test void aprovadoNaoPodeSerCanceladoComoPendente() {
        prepararCancelamento("APROVADO");
        assertThrows(com.bevans.exception.SolicitacaoConflictException.class,()->service.cancelarSolicitacao(3L,2L));
        verify(vinculos,never()).save(any());
    }
    @Test void serviceTambemBloqueiaTitularDiferente() {
        prepararCancelamento("PENDENTE");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.cancelarSolicitacao(3L,99L));
        verify(vinculos,never()).save(any());
    }
}
