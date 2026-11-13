package com.bevans.repository;

import com.bevans.exception.CapacidadeException;
import com.bevans.model.*;
import com.bevans.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.sql.init.mode=never"})
@Import(TurmaService.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class TurmaCapacidadeConcorrenciaTest {
    @Autowired TurmaService service;
    @Autowired TurmaRepository turmas;
    @Autowired UsuarioRepository usuarios;
    @Autowired AlunoTurmaRepository vinculos;
    @Autowired PlatformTransactionManager transactions;
    @MockBean GeocodingService geocoding;

    private Usuario usuario(String tipo) {
        String id=java.util.UUID.randomUUID().toString();
        Usuario u=new Usuario(); u.setNome(id); u.setEmail(id+"@example.test"); u.setTelefone(id); u.setTipo(tipo);
        return usuarios.save(u);
    }
    private Long[] preparar(int capacidade) {
        return new TransactionTemplate(transactions).execute(tx->{
            Turma t=new Turma(); t.setNome("Teste"); t.setMotorista(usuario("MOTORISTA")); t.setCapacidade(capacidade);
            t=turmas.save(t);
            Long[] ids={t.getId(),null,null};
            for(int i=1;i<=2;i++) {
                AlunoTurma v=new AlunoTurma(); v.setTurma(t); v.setAluno(usuario("PASSAGEIRO")); v.setStatus("PENDENTE");
                ids[i]=vinculos.save(v).getId();
            }
            return ids;
        });
    }
    private int corrida(Runnable primeira, Runnable segunda) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch prontos=new CountDownLatch(2), inicio=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Boolean>> tarefa=acao->()->{
            prontos.countDown();
            if(!inicio.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier timeout");
            try { acao.run(); return true; } catch(CapacidadeException | IllegalArgumentException conflito) { return false; }
        };
        try {
            Future<Boolean> a=pool.submit(tarefa.apply(primeira)), b=pool.submit(tarefa.apply(segunda));
            assertTrue(prontos.await(5,TimeUnit.SECONDS)); inicio.countDown();
            return (a.get(10,TimeUnit.SECONDS)?1:0)+(b.get(10,TimeUnit.SECONDS)?1:0);
        } finally { inicio.countDown(); pool.shutdownNow(); }
    }
    @Test void duasAprovacoesNaUltimaVagaConfirmamSomenteUmPassageiro() throws Exception {
        Long[] ids=preparar(1);
        assertEquals(1,corrida(()->service.analisarSolicitacao(ids[1],"APROVADO"),()->service.analisarSolicitacao(ids[2],"APROVADO")));
        assertEquals(1,vinculos.countByTurmaIdAndStatus(ids[0],"APROVADO"));
        assertEquals(1,vinculos.countByTurmaIdAndStatus(ids[0],"PENDENTE"));
    }
    @Test void reduzirCapacidadeEAprovarSimultaneamenteMantemOcupacaoConsistente() throws Exception {
        Long[] ids=preparar(2);
        service.analisarSolicitacao(ids[1],"APROVADO");
        Turma patch=new Turma(); patch.setCapacidade(1);
        assertEquals(1,corrida(()->service.atualizarTurma(ids[0],patch),()->service.analisarSolicitacao(ids[2],"APROVADO")));
        var vagas=service.consultarVagas(ids[0]);
        assertTrue(vagas.ocupados()<=vagas.capacidade());
    }
    @Test void pedidosConcorrentesDoMesmoPassageiroNaoDuplicamVinculo() throws Exception {
        Long[] ids=preparar(12);
        String codigo=java.util.UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
        Long alunoId=new TransactionTemplate(transactions).execute(tx->{
            Turma t=turmas.findById(ids[0]).orElseThrow(); t.setCodigoConvite(codigo); turmas.save(t);
            return usuario("PASSAGEIRO").getId();
        });
        assertEquals(1,corrida(()->service.solicitarEntradaPorCodigo(alunoId,codigo),()->service.solicitarEntradaPorCodigo(alunoId,codigo)));
        assertTrue(vinculos.findByTurmaIdAndAlunoId(ids[0],alunoId).isPresent());
        assertEquals(3,vinculos.countByTurmaIdAndStatus(ids[0],"PENDENTE"));
    }
    @Test void cancelarEAprovarMesmaSolicitacaoNaoSobrescrevemEstadoFinal() throws Exception {
        Long[] ids=preparar(1);
        Long alunoId=vinculos.findById(ids[1]).orElseThrow().getAluno().getId();
        assertEquals(1,corrida(()->service.cancelarSolicitacao(ids[1],alunoId),()->service.analisarSolicitacao(ids[1],"APROVADO")));
        String status=vinculos.statusAtual(ids[1]);
        assertTrue("CANCELADO".equals(status)||"APROVADO".equals(status));
        assertEquals("APROVADO".equals(status)?1:0,vinculos.countByTurmaIdAndStatus(ids[0],"APROVADO"));
    }
}
