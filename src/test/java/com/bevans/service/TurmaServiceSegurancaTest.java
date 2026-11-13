package com.bevans.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bevans.model.AlunoTurma;
import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.AlunoTurmaRepository;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.ViagemRepository;

@ExtendWith(MockitoExtension.class)
class TurmaServiceSegurancaTest {
    @Mock TurmaRepository turmas;
    @Mock UsuarioRepository usuarios;
    @Mock AlunoTurmaRepository vinculos;
    @Mock ViagemRepository viagens;
    @Mock GeocodingService geocoding;
    @Mock jakarta.persistence.EntityManager entityManager;

    TurmaService service;

    @BeforeEach
    void configurar() {
        service = new TurmaService(turmas, usuarios, vinculos, viagens, geocoding, entityManager);
    }

    private void prepararConvite(Usuario usuario) {
        Turma turma = new Turma();
        turma.setId(13L);
        when(turmas.findByCodigoConvite("ABC123")).thenReturn(Optional.of(turma));
        when(turmas.lockById(13L)).thenReturn(Optional.of(turma));
        when(usuarios.findById(usuario.getId())).thenReturn(Optional.of(usuario));
    }

    @Test
    void motoristaNaoPodeSolicitarEntradaComoPassageiro() {
        Usuario motorista = new Usuario();
        motorista.setId(1L);
        motorista.setTipo("MOTORISTA");
        prepararConvite(motorista);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.solicitarEntradaPorCodigo(1L, "ABC123"));
        assertTrue(e.getMessage().contains("Somente passageiros"));
        verify(vinculos, never()).save(any(AlunoTurma.class));
    }

    @Test
    void passageiroCriaSolicitacaoPendente() {
        Usuario passageiro = new Usuario();
        passageiro.setId(2L);
        passageiro.setTipo("PASSAGEIRO");
        prepararConvite(passageiro);
        when(vinculos.findByTurmaIdAndAlunoId(13L, 2L))
                .thenReturn(Optional.empty());

        service.solicitarEntradaPorCodigo(2L, "ABC123");

        verify(vinculos).save(argThat(v -> "PENDENTE".equals(v.getStatus())
                && v.getAluno() == passageiro
                && v.getTurma().getId().equals(13L)));
    }

    @Test
    void statusInvalidoNaoAlteraVinculo() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analisarSolicitacao(11L, "INVALIDO"));
        verify(vinculos, never()).save(any(AlunoTurma.class));
    }

    @Test
    void motoristaPodeAprovarSolicitacaoPendente() {
        AlunoTurma vinculo = new AlunoTurma();
        vinculo.setStatus("PENDENTE");
        when(vinculos.findById(11L)).thenReturn(Optional.of(vinculo));
        prepararAnalise("PENDENTE", true);

        service.analisarSolicitacao(11L, "APROVADO");

        assertEquals("APROVADO", vinculo.getStatus());
        verify(vinculos).save(vinculo);
    }

    @Test
    void motoristaPodeRejeitarSolicitacaoPendente() {
        AlunoTurma vinculo = new AlunoTurma();
        vinculo.setStatus("PENDENTE");
        when(vinculos.findById(11L)).thenReturn(Optional.of(vinculo));
        prepararAnalise("PENDENTE", false);

        service.analisarSolicitacao(11L, "REJEITADO");

        assertEquals("REJEITADO", vinculo.getStatus());
        verify(vinculos).save(vinculo);
    }

    @Test
    void naoPermiteReanalisarSolicitacaoRejeitada() {
        AlunoTurma vinculo = new AlunoTurma();
        vinculo.setStatus("REJEITADO");
        prepararAnalise("REJEITADO", false);

        assertThrows(IllegalArgumentException.class,
                () -> service.analisarSolicitacao(11L, "APROVADO"));
        assertEquals("REJEITADO", vinculo.getStatus());
        verify(vinculos, never()).save(any(AlunoTurma.class));
    }

    private void prepararAnalise(String status, boolean aprovar) {
        when(vinculos.turmaIdDoVinculo(11L)).thenReturn(Optional.of(13L));
        when(turmas.lockById(13L)).thenReturn(Optional.of(new Turma()));
        when(vinculos.statusAtual(11L)).thenReturn(status);
        if (aprovar) when(turmas.capacidadeAtual(13L)).thenReturn(12);
    }
}
