package com.bevans.service;

import com.bevans.dto.PassageiroRotaDTO;
import com.bevans.model.*;
import com.bevans.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RotaServiceTest {
    @Mock PresencaRepository presencas;
    @Mock TurmaRepository turmas;
    @Mock EnderecoRepository enderecos;
    RotaService service;
    Turma turma;

    @BeforeEach
    void configurar() {
        service = new RotaService(presencas, turmas, enderecos);
        turma = turma(1L, -8.0, -8.1);
    }

    private Turma turma(long id, double origem, double destino) {
        Usuario motorista = new Usuario();
        motorista.setId(id + 100);
        motorista.setLatitude(origem);
        motorista.setLongitude(-35.0);
        Turma t = new Turma();
        t.setId(id);
        t.setMotorista(motorista);
        t.setOrigemLatitude(origem);
        t.setOrigemLongitude(-35.0);
        t.setDestinoLatitude(destino);
        t.setDestinoLongitude(-35.0);
        return t;
    }

    private Presenca presenca(long id, double latitude) {
        Usuario aluno = new Usuario();
        aluno.setId(id);
        aluno.setNome("Passageiro " + id);
        Endereco endereco = new Endereco();
        endereco.setUsuario(aluno);
        endereco.setLatitude(latitude);
        endereco.setLongitude(-35.0);
        Presenca p = new Presenca();
        p.setUsuario(aluno);
        p.setTurma(turma);
        p.setStatus("AMBOS");
        p.setEnderecoIda(endereco);
        p.setEnderecoVolta(endereco);
        return p;
    }

    private void preparar(Turma t, Presenca... passageiros) {
        when(turmas.findById(t.getId())).thenReturn(Optional.of(t));
        when(presencas.findByDataAndTurmaId(LocalDate.now(ZoneId.of("America/Recife")), t.getId()))
                .thenReturn(List.of(passageiros));
    }

    private List<Long> ids(String sentido, long turmaId) {
        return service.otimizarRota(sentido, turmaId).join().stream()
                .map(PassageiroRotaDTO::getId).toList();
    }

    @Test
    void origemEDestinoDeterminamOrdemNosDoisSentidosSemDuplicacao() {
        preparar(turma, presenca(2, -8.08), presenca(3, -8.02));
        assertEquals(List.of(3L, 2L), ids("IDA", 1));
        assertEquals(List.of(2L, 3L), ids("VOLTA", 1));
    }

    @Test
    void matrizViariaAssimetricaCorrigeOrdemNaVoltaSemTrocarExtremos() {
        preparar(turma,presenca(2,-8.08),presenca(3,-8.02),presenca(4,-8.05));
        var matriz=mock(MatrizViariaMapbox.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"matrizViaria",matriz);
        when(matriz.habilitada()).thenReturn(true);
        // Índices: garagem=0, passageiros=1,2,3, faculdade=4.
        long[][] custo=new long[5][5];
        for(int i=0;i<5;i++)for(int j=0;j<5;j++)custo[i][j]=i==j?0:10000;
        custo[4][2]=10;custo[2][3]=10;custo[3][1]=10;custo[1][0]=10;
        custo[0][1]=20;custo[1][3]=20;custo[3][2]=20;custo[2][4]=20;
        when(matriz.calcular(any(double[].class),any(double[].class))).thenReturn(custo);
        assertEquals(List.of(3L,4L,2L),ids("VOLTA",1));
        assertEquals(List.of(2L,4L,3L),ids("IDA",1));
        verify(matriz,times(2)).calcular(any(double[].class),any(double[].class));
    }

    @Test
    void matrizIndisponivelNaoFingeOtimizacaoViaria() {
        preparar(turma,presenca(2,-8.08),presenca(3,-8.02));
        var matriz=mock(MatrizViariaMapbox.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"matrizViaria",matriz);
        when(matriz.habilitada()).thenReturn(true);
        when(matriz.calcular(any(double[].class),any(double[].class))).thenThrow(
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Matriz indisponível"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ids("VOLTA",1));
    }

    @Test
    void duasTurmasUsamOrigemDoRespectivoMotoristaSemEscolhaGlobal() {
        turma.setOrigemLatitude(null);
        Turma outra = turma(2, -8.1, -8.0);
        outra.setOrigemLatitude(null);
        preparar(turma, presenca(2, -8.08), presenca(3, -8.02));
        preparar(outra, presenca(4, -8.08), presenca(5, -8.02));
        assertEquals(List.of(3L, 2L), ids("IDA", 1));
        assertEquals(List.of(4L, 5L), ids("IDA", 2));
    }

    @Test
    void semMotoristaFalhaSemBuscarPassageiros() {
        turma.setMotorista(null);
        when(turmas.findById(1L)).thenReturn(Optional.of(turma));
        assertThrows(RuntimeException.class, () -> service.otimizarRota("IDA", 1L));
        verifyNoInteractions(presencas);
    }

    @Test
    void zeroEUmPassageiroFuncionamNosDoisSentidos() {
        preparar(turma);
        assertTrue(ids("IDA", 1).isEmpty());
        assertTrue(ids("VOLTA", 1).isEmpty());
        preparar(turma, presenca(2, -8.05));
        assertEquals(List.of(2L), ids("IDA", 1));
        assertEquals(List.of(2L), ids("VOLTA", 1));
    }

    @Test
    void selecionaEnderecoPorSentidoERecalculaAposAlteracao() {
        Presenca p = presenca(2, -8.02);
        Endereco volta = presenca(2, -8.08).getEnderecoVolta();
        p.setEnderecoVolta(volta);
        preparar(turma, p);
        assertEquals(-8.02, service.otimizarRota("IDA", 1L).join().getFirst().getLatitude());
        assertEquals(-8.08, service.otimizarRota("VOLTA", 1L).join().getFirst().getLatitude());
        volta.setLatitude(-8.06);
        assertEquals(-8.06, service.otimizarRota("VOLTA", 1L).join().getFirst().getLatitude());
        verifyNoInteractions(enderecos);
    }

    @Test
    void enderecoAusenteNaoUsaOutroSentidoNemLegadoParaTurmaAtual() {
        Presenca p = presenca(2, -8.02);
        p.setEndereco(p.getEnderecoVolta());
        p.setEnderecoIda(null);
        preparar(turma, p);
        assertPontoInvalido();
        assertEquals(List.of(2L), ids("VOLTA", 1));
        verifyNoInteractions(enderecos);
    }

    @Test
    void legadoSemTurmaUsaEnderecoExplicito() {
        Presenca p = presenca(2, -8.02);
        p.setTurma(null);
        p.setEndereco(p.getEnderecoIda());
        p.setEnderecoIda(null);
        p.setEnderecoVolta(null);
        preparar(turma, p);
        assertEquals(List.of(2L), ids("IDA", 1));
        assertEquals(List.of(2L), ids("VOLTA", 1));
    }

    @Test
    void enderecoDeTerceiroOuSemProprietarioNaoEntraNaRota() {
        Presenca p = presenca(2, -8.02);
        p.getEnderecoIda().setUsuario(presenca(3, -8.03).getUsuario());
        preparar(turma, p);
        assertPontoInvalido();
        p.getEnderecoIda().setUsuario(null);
        assertPontoInvalido();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "91,-35", "-91,-35", "-8,181", "-8,-181", "NaN,-35", "-8,Infinity"})
    void coordenadasInvalidasNaoEntramNaRota(double lat, double lon) {
        Presenca p = presenca(2, lat);
        p.getEnderecoIda().setLongitude(lon);
        preparar(turma, p);
        assertPontoInvalido();
    }

    @Test
    void coordenadasNulasNaoEntramNaRota() {
        Presenca p = presenca(2, -8.02);
        p.getEnderecoIda().setLatitude(null);
        preparar(turma, p);
        assertPontoInvalido();
    }

    private void assertPontoInvalido(){
        var erro=assertThrows(com.bevans.exception.ConflitoOperacionalException.class,()->ids("IDA",1));
        assertEquals(409,erro.getStatusCode().value());assertEquals("PONTO_INVALIDO",erro.getCodigo());
        assertTrue(erro.getReason().contains("IDA"));
    }

    @Test void umEnderecoInvalidoNaoRetornaRoteiroParcial(){
        var invalida=presenca(3,-8.03);invalida.setEnderecoIda(null);
        preparar(turma,presenca(2,-8.02),invalida);assertPontoInvalido();
    }

    @Test
    void origemAusenteNoMotoristaETurmaImpedeOtimizacao() {
        turma.setOrigemLatitude(null);
        turma.getMotorista().setLatitude(null);
        preparar(turma, presenca(2, -8.02));
        assertThrows(RuntimeException.class, () -> service.otimizarRota("IDA", 1L));
    }

    @Test
    void destinoInvalidoImpedeOtimizacaoComPassageiros() {
        turma.setDestinoLatitude(0.0);
        turma.setDestinoLongitude(0.0);
        preparar(turma, presenca(2, -8.02));
        assertThrows(RuntimeException.class, () -> service.otimizarRota("IDA", 1L));
    }

    @Test
    void parametrosInvalidosNaoConsultamBanco() {
        assertThrows(IllegalArgumentException.class, () -> service.otimizarRota("INVALIDO", 1L));
        assertThrows(IllegalArgumentException.class, () -> service.otimizarRota(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.otimizarRota("IDA", null));
        verifyNoInteractions(turmas, presencas);
    }

    @ParameterizedTest
    @CsvSource({"IDA,IDA,true", "IDA,VOLTA,false", "VOLTA,IDA,false", "VOLTA,VOLTA,true", "FALTOU,IDA,false", "AMBOS,VOLTA,true"})
    void respeitaPresencaPorSentido(String status, String sentido, boolean incluido) {
        Presenca p = presenca(2, -8.02);
        p.setStatus(status);
        preparar(turma, p);
        assertEquals(incluido ? List.of(2L) : List.of(), ids(sentido, 1));
    }

    @Test
    void destinoInvalidoSemPassageirosTambemFalha() {
        turma.setDestinoLatitude(0.0);
        turma.setDestinoLongitude(0.0);

        preparar(turma);

        IllegalArgumentException erro = assertThrows(
                IllegalArgumentException.class,
                () -> service.otimizarRota("IDA", 1L)
        );

        assertTrue(erro.getMessage().contains("destino"));
    }

    @Test
    void tresPassageirosMantemSequenciaSemOmissoesOuDuplicacoes() {
        preparar(
                turma,
                presenca(2, -8.08),
                presenca(3, -8.02),
                presenca(4, -8.05)
        );

        assertEquals(
                List.of(3L, 4L, 2L),
                ids("IDA", 1L)
        );

        assertEquals(
                List.of(2L, 4L, 3L),
                ids("VOLTA", 1L)
        );
    }

}
