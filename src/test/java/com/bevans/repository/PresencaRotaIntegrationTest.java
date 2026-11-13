package com.bevans.repository;

import com.bevans.model.*;
import com.bevans.service.RotaService;
import com.bevans.service.ViagemService;
import com.bevans.exception.ConflitoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Executa o JPQL real e o OR-Tools com banco descartável, sem Firebase/PostgreSQL. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class PresencaRotaIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired PresencaRepository presencas;
    @Autowired TurmaRepository turmas;
    @Autowired EnderecoRepository enderecos;
    @Autowired ViagemRepository viagens;
    @Autowired PosicaoViagemRepository posicoes;

    private Usuario usuario(String nome, String tipo) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(nome + "@example.test");
        u.setTelefone(nome);
        u.setTipo(tipo);
        return em.persist(u);
    }

    private Turma turma(Usuario motorista) {
        Turma t = new Turma();
        t.setMotorista(motorista);
        t.setOrigemLatitude(-8.0);
        t.setOrigemLongitude(-35.0);
        t.setDestinoLatitude(-8.1);
        t.setDestinoLongitude(-35.0);
        return em.persist(t);
    }

    private AlunoTurma vinculo(Usuario aluno, Turma turma, String status) {
        AlunoTurma v = new AlunoTurma();
        v.setAluno(aluno);
        v.setTurma(turma);
        v.setStatus(status);
        return em.persist(v);
    }

    private void presenca(Usuario aluno, Turma turma, LocalDate data) {
        Endereco e = new Endereco();
        e.setUsuario(aluno);
        e.setLatitude(-8.05);
        e.setLongitude(-35.0);
        em.persist(e);
        Presenca p = new Presenca();
        p.setUsuario(aluno);
        p.setTurma(turma);
        p.setData(data);
        p.setStatus("AMBOS");
        p.setEnderecoIda(e);
        p.setEnderecoVolta(e);
        em.persist(p);
    }

    @Test
    void duasTurmasExcluemPendentesRejeitadosRemovidosSemVinculoEOutroDia() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Recife"));
        Turma primeira = turma(usuario("motorista1", "MOTORISTA"));
        Turma segunda = turma(usuario("motorista2", "MOTORISTA"));
        Usuario aprovado1 = usuario("aprovado1", "PASSAGEIRO");
        Usuario aprovado2 = usuario("aprovado2", "PASSAGEIRO");
        vinculo(aprovado1, primeira, "APROVADO");
        vinculo(aprovado2, segunda, "APROVADO");
        presenca(aprovado1, primeira, hoje);
        presenca(aprovado2, segunda, hoje);
        // Presença na turma errada não basta: o vínculo aprovado também deve corresponder.
        presenca(aprovado2, primeira, hoje);
        for (String status : List.of("PENDENTE", "REJEITADO", "REMOVIDO", "SEM_VINCULO", "OUTRO_DIA")) {
            Usuario aluno = usuario(status, "PASSAGEIRO");
            if (!status.equals("SEM_VINCULO")) {
                AlunoTurma v = vinculo(aluno, primeira,
                        status.equals("PENDENTE") || status.equals("REJEITADO") ? status : "APROVADO");
                if (status.equals("REMOVIDO")) em.remove(v);
            }
            presenca(aluno, primeira, status.equals("OUTRO_DIA") ? hoje.minusDays(1) : hoje);
        }
        em.flush();
        Long primeiraId = primeira.getId();
        Long segundaId = segunda.getId();
        Long aluno1Id = aprovado1.getId();
        Long aluno2Id = aprovado2.getId();
        em.clear();

        RotaService service = new RotaService(presencas, turmas, enderecos);
        for (String sentido : List.of("IDA", "VOLTA")) {
            assertEquals(List.of(aluno1Id), service.otimizarRota(sentido, primeiraId).join()
                    .stream().map(p -> p.getId()).toList());
            assertEquals(List.of(aluno2Id), service.otimizarRota(sentido, segundaId).join()
                    .stream().map(p -> p.getId()).toList());
        }
    }

    @Test
    void novaConsultaRefleteEnderecoPersistidoEVinculoRevogado() {
        Turma t = turma(usuario("motorista", "MOTORISTA"));
        Usuario aluno = usuario("aluno", "PASSAGEIRO");
        AlunoTurma v = vinculo(aluno, t, "APROVADO");
        presenca(aluno, t, LocalDate.now(ZoneId.of("America/Recife")));
        em.flush();
        Long turmaId = t.getId();
        Long vinculoId = v.getId();
        Long enderecoId = presencas.findByDataAndTurmaId(
                LocalDate.now(ZoneId.of("America/Recife")), turmaId).getFirst().getEnderecoIda().getId();
        em.clear();
        RotaService service = new RotaService(presencas, turmas, enderecos);
        assertEquals(-8.05, service.otimizarRota("IDA", turmaId).join().getFirst().getLatitude());

        em.find(Endereco.class, enderecoId).setLatitude(-8.07);
        em.flush();
        em.clear();
        assertEquals(-8.07, service.otimizarRota("IDA", turmaId).join().getFirst().getLatitude());

        em.remove(em.find(AlunoTurma.class, vinculoId));
        em.flush();
        em.clear();
        assertEquals(List.of(), service.otimizarRota("IDA", turmaId).join());
    }

    @Test
    void viagemContinuaAtivaAposRecriarServico() {
        Turma t = turma(usuario("motorista-persistencia", "MOTORISTA"));
        em.flush();

        Long turmaId = t.getId();

        ViagemService primeiro =
                new ViagemService(viagens, turmas, posicoes);

        primeiro.iniciarRota(turmaId, "IDA");
        em.flush();
        em.clear();

        ViagemService segundo =
                new ViagemService(viagens, turmas, posicoes);

        assertEquals(true, segundo.rotaAtiva(turmaId));
        assertEquals(
                "ATIVA",
                segundo.verificarStatusAtual(turmaId).get("status")
        );

        segundo.encerrarRota(turmaId);
        em.flush();
        em.clear();

        ViagemService terceiro =
                new ViagemService(viagens, turmas, posicoes);

        assertEquals(false, terceiro.rotaAtiva(turmaId));
        assertEquals(
                "INATIVA",
                terceiro.verificarStatusAtual(turmaId).get("status")
        );
    }

    @Test
    void naoPermiteDuasViagensAtivasNaMesmaTurma() {
        Turma t = turma(usuario("motorista-duplicidade", "MOTORISTA"));
        em.flush();

        ViagemService service =
                new ViagemService(viagens, turmas, posicoes);

        service.iniciarRota(t.getId(), "IDA");
        em.flush();
        em.clear();

        org.junit.jupiter.api.Assertions.assertThrows(
                ConflitoException.class,
                () -> new ViagemService(viagens, turmas, posicoes)
                        .iniciarRota(t.getId(), "VOLTA")
        );
    }

}
