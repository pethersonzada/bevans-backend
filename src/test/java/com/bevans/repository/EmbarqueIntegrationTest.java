package com.bevans.repository;

import com.bevans.config.TempoConfig;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import com.bevans.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({EmbarqueService.class,RotaPublicaService.class,TurmaService.class,TempoConfig.class,ViagemService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class EmbarqueIntegrationTest {
    @Autowired EmbarqueService service;
    @Autowired RotaPublicaService rotas;
    @Autowired TurmaService turmas;
    @Autowired UsuarioRepository usuarios;
    @Autowired TurmaRepository turmaRepository;
    @Autowired EmbarqueRepository pedidos;
    @Autowired AlunoTurmaRepository vinculos;
    @Autowired ViagemService viagens;
    @Autowired Clock clock;
    @MockBean GeocodingService geocoding;

    private record Cenario(Long rota,Long turma,Long dono,Long parada) {}
    private Usuario usuario(String tipo) {
        String id=UUID.randomUUID().toString();Usuario u=new Usuario();u.setNome(id);u.setTelefone(id);u.setEmail(id+"@example.test");u.setTipo(tipo);return usuarios.save(u);
    }
    private Cenario cenario(ModoRota modo,int capacidade) {
        Usuario dono=usuario("MOTORISTA");Turma t=new Turma();t.setNome("Privada");t.setTurno("NOITE");t.setMotorista(dono);t.setCapacidade(capacidade);t=turmas.criarTurma(t);
        var d=new RotaPublicaRequest(t.getId(),"Campus","Caruaru",CategoriaRota.UNIVERSITARIA,modo,"Terminal",-8.2,-35.9,"Campus",-8.3,-36.0,null,null,null,
                EnumSet.allOf(DayOfWeek.class),LocalTime.of(7,0),LocalTime.of(18,0));
        var r=rotas.criar(dono.getId(),d);rotas.publicar(r.id(),dono.getId(),true);
        var parada=rotas.salvarParadas(r.id(),dono.getId(),List.of(new ParadaPublicaDTO(null,"Terminal B",-8.25,-35.95,null))).paradas().get(0);
        return new Cenario(r.id(),t.getId(),dono.getId(),parada.id());
    }
    private LocalDate data(){return LocalDate.now(clock).plusDays(2);}
    private EmbarqueRequest fixo(Cenario c){return new EmbarqueRequest(data(),"IDA",c.parada(),null,null,null,null,null);}
    private EmbarqueRequest flexivel(){return new EmbarqueRequest(data(),"IDA",null,"Pin residencial confirmado",-8.26,-35.96,true,"Portão lateral");}

    @Test void fixaExigeParadaExistenteDaPropriaRota() {
        var c=cenario(ModoRota.FIXA,12);Long aluno=usuario("PASSAGEIRO").getId();
        var e=service.solicitar(c.rota(),aluno,fixo(c));assertEquals(StatusEmbarque.PENDENTE,e.status());assertEquals(c.parada(),e.paradaId());
        assertEquals(-8.25,e.latitude());
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,flexivel()));
        var outra=cenario(ModoRota.FIXA,12);
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,fixo(outra)));
    }
    @Test void excecaoFixaDependeDeOptInDoDonoEPermanecePrivada() {
        var c=cenario(ModoRota.FIXA,12);Long aluno=usuario("PASSAGEIRO").getId();
        assertFalse(rotas.consultar(c.rota()).permitePontoExcepcional());
        assertThrows(AccessDeniedException.class,()->rotas.permitirPontoExcepcional(c.rota(),usuario("MOTORISTA").getId(),true));
        assertTrue(rotas.permitirPontoExcepcional(c.rota(),c.dono(),true).permitePontoExcepcional());
        var e=service.solicitar(c.rota(),aluno,flexivel());
        assertEquals(3,service.planejamento(c.rota(),c.dono(),data(),"IDA",true).size());
        rotas.permitirPontoExcepcional(c.rota(),c.dono(),false);
        assertThrows(SolicitacaoConflictException.class,()->service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO));
        rotas.permitirPontoExcepcional(c.rota(),c.dono(),true);
        service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO);
        rotas.permitirPontoExcepcional(c.rota(),c.dono(),false);
        var pontos=service.planejamento(c.rota(),c.dono(),data(),"IDA",true);
        assertEquals(4,pontos.size());assertEquals(c.parada(),pontos.get(1).paradaId());
        assertEquals(1,rotas.consultar(c.rota()).paradas().size());
        viagens.iniciarRota(c.turma(),"IDA");
        assertThrows(SolicitacaoConflictException.class,()->rotas.permitirPontoExcepcional(c.rota(),c.dono(),true));
    }
    @Test void pinPendenteNaoEntraNaRotaEAprovadoContinuaPrivado() {
        var c=cenario(ModoRota.FLEXIVEL,12);Long aluno=usuario("PASSAGEIRO").getId();
        var e=service.solicitar(c.rota(),aluno,flexivel());
        assertEquals(3,service.planejamento(c.rota(),c.dono(),data(),"IDA",true).size());
        service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO);
        var execucao=service.planejamento(c.rota(),c.dono(),data(),"IDA",true);assertEquals(4,execucao.size());
        assertTrue(execucao.stream().anyMatch(p->p.nome().equals("Pin residencial confirmado")));
        assertEquals(1,rotas.consultar(c.rota()).paradas().size());
        assertEquals(3,rotas.planejamento(c.rota(),c.dono(),"IDA",true).size());
        assertEquals(1,turmas.consultarVagas(c.turma()).ocupados());
        assertEquals(StatusEmbarque.APROVADO,service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO).status());
        assertEquals(1,turmas.consultarVagas(c.turma()).ocupados());
    }
    @Test void repeticaoIdempotenteECancelamentoPreservaHistorico() {
        var c=cenario(ModoRota.FLEXIVEL,12);Long aluno=usuario("PASSAGEIRO").getId();
        var a=service.solicitar(c.rota(),aluno,flexivel());var b=service.solicitar(c.rota(),aluno,flexivel());assertEquals(a.id(),b.id());
        assertThrows(SolicitacaoConflictException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(data(),"IDA",null,"Outro pin",-8.27,-35.97,true,null)));
        service.cancelar(a.id(),aluno);assertEquals(StatusEmbarque.CANCELADO,service.cancelar(a.id(),aluno).status());
        var novo=service.solicitar(c.rota(),aluno,flexivel());assertNotEquals(a.id(),novo.id());
        assertEquals(2,service.minhas(aluno).size());
    }
    @Test void autorizacaoEDadosSaoIsoladosPorTitularERota() {
        var c=cenario(ModoRota.FLEXIVEL,12);Long aluno=usuario("PASSAGEIRO").getId(),outro=usuario("PASSAGEIRO").getId(),motorista=usuario("MOTORISTA").getId();
        var e=service.solicitar(c.rota(),aluno,flexivel());
        assertTrue(service.minhas(outro).isEmpty());assertEquals(1,service.minhas(aluno).size());
        assertThrows(AccessDeniedException.class,()->service.cancelar(e.id(),outro));
        assertThrows(AccessDeniedException.class,()->service.analisar(e.id(),motorista,StatusEmbarque.APROVADO));
        assertThrows(AccessDeniedException.class,()->service.daRota(c.rota(),motorista,data(),"IDA"));
        assertThrows(AccessDeniedException.class,()->service.solicitar(c.rota(),motorista,flexivel()));
    }
    @Test void validaPinExplicitoCoordenadasEDatas() {
        var c=cenario(ModoRota.FLEXIVEL,12);Long aluno=usuario("PASSAGEIRO").getId();
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(data(),"IDA",null,"Rua",-8.0,-35.0,false,null)));
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(data(),"IDA",null,"Rua",Double.NaN,-35.0,true,null)));
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(LocalDate.now(clock).minusDays(1),"IDA",c.parada(),null,null,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(LocalDate.now(clock).plusDays(31),"IDA",c.parada(),null,null,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(data(),"INVALIDO",c.parada(),null,null,null,null,null)));
    }
    @Test void aprovarImpedeRemoverOuMudarPontoConfirmado() {
        var c=cenario(ModoRota.FIXA,12);var e=service.solicitar(c.rota(),usuario("PASSAGEIRO").getId(),fixo(c));
        service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO);
        assertThrows(SolicitacaoConflictException.class,()->rotas.salvarParadas(c.rota(),c.dono(),List.of()));
        assertThrows(SolicitacaoConflictException.class,()->rotas.salvarParadas(c.rota(),c.dono(),List.of(new ParadaPublicaDTO(c.parada(),"B",-8.27,-35.97,null))));
    }
    @Test void viagemAtivaBloqueiaSolicitacaoAnaliseCancelamentoEPlano() {
        var c=cenario(ModoRota.FLEXIVEL,12);Long aluno=usuario("PASSAGEIRO").getId();var e=service.solicitar(c.rota(),aluno,flexivel());
        viagens.iniciarRota(c.turma(),"IDA");
        assertThrows(SolicitacaoConflictException.class,()->service.solicitar(c.rota(),aluno,flexivel()));
        assertThrows(SolicitacaoConflictException.class,()->service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO));
        assertThrows(SolicitacaoConflictException.class,()->service.cancelar(e.id(),aluno));
        assertThrows(SolicitacaoConflictException.class,()->service.planejamento(c.rota(),c.dono(),data(),"IDA",true));
    }
    private int corrida(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch prontos=new CountDownLatch(2),inicio=new CountDownLatch(1);
        java.util.function.Function<Runnable,Callable<Boolean>> tarefa=acao->()->{prontos.countDown();assertTrue(inicio.await(5,TimeUnit.SECONDS));try{acao.run();return true;}catch(CapacidadeException|SolicitacaoConflictException esperado){return false;}};
        try{var x=pool.submit(tarefa.apply(a));var y=pool.submit(tarefa.apply(b));assertTrue(prontos.await(5,TimeUnit.SECONDS));inicio.countDown();return(x.get(15,TimeUnit.SECONDS)?1:0)+(y.get(15,TimeUnit.SECONDS)?1:0);}
        finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void aprovacoesSimultaneasDaUltimaVagaNaoSuperlotam() throws Exception {
        var c=cenario(ModoRota.FLEXIVEL,1);var a=service.solicitar(c.rota(),usuario("PASSAGEIRO").getId(),flexivel());var b=service.solicitar(c.rota(),usuario("PASSAGEIRO").getId(),flexivel());
        assertEquals(1,corrida(()->service.analisar(a.id(),c.dono(),StatusEmbarque.APROVADO),()->service.analisar(b.id(),c.dono(),StatusEmbarque.APROVADO)));
        assertEquals(1,turmas.consultarVagas(c.turma()).ocupados());
        assertEquals(1,service.daRota(c.rota(),c.dono(),data(),"IDA").stream().filter(e->e.status()==StatusEmbarque.APROVADO).count());
    }
    @Test void analiseECancelamentoConcorrentesNaoSobrescrevemEstado() throws Exception {
        var c=cenario(ModoRota.FIXA,1);Long aluno=usuario("PASSAGEIRO").getId();var e=service.solicitar(c.rota(),aluno,fixo(c));
        assertEquals(1,corrida(()->service.analisar(e.id(),c.dono(),StatusEmbarque.APROVADO),()->service.cancelar(e.id(),aluno)));
        var status=service.minhas(aluno).get(0).status();assertTrue(status==StatusEmbarque.APROVADO||status==StatusEmbarque.CANCELADO);
        assertEquals(status==StatusEmbarque.APROVADO?1:0,turmas.consultarVagas(c.turma()).ocupados());
    }
    @Test void mesmoPassageiroEmDoisSentidosNaoOcupaDuasVagas() {
        var c=cenario(ModoRota.FIXA,1);Long aluno=usuario("PASSAGEIRO").getId();
        var ida=service.solicitar(c.rota(),aluno,fixo(c));service.analisar(ida.id(),c.dono(),StatusEmbarque.APROVADO);
        var volta=service.solicitar(c.rota(),aluno,new EmbarqueRequest(data(),"VOLTA",c.parada(),null,null,null,null,null));
        service.analisar(volta.id(),c.dono(),StatusEmbarque.APROVADO);
        assertEquals(1,turmas.consultarVagas(c.turma()).ocupados());
    }
    @Test void rejeicaoMantemAuditoriaESemVagaOuPontoIncluido() {
        var c=cenario(ModoRota.FLEXIVEL,1);Long aluno=usuario("PASSAGEIRO").getId();var e=service.solicitar(c.rota(),aluno,flexivel());
        var rejeitado=service.analisar(e.id(),c.dono(),StatusEmbarque.REJEITADO);
        assertNotNull(rejeitado.analisadoEm());assertEquals(StatusEmbarque.REJEITADO,rejeitado.status());
        assertEquals(0,turmas.consultarVagas(c.turma()).ocupados());assertEquals(3,service.planejamento(c.rota(),c.dono(),data(),"IDA",false).size());
        assertNotEquals(e.id(),service.solicitar(c.rota(),aluno,flexivel()).id());
    }
    @Test void duplicacaoConcorrenteRetornaMesmoPedido() throws Exception {
        var c=cenario(ModoRota.FLEXIVEL,1);Long aluno=usuario("PASSAGEIRO").getId();
        assertEquals(2,corrida(()->service.solicitar(c.rota(),aluno,flexivel()),()->service.solicitar(c.rota(),aluno,flexivel())));
        assertEquals(1,service.minhas(aluno).size());
    }
    @Test void diasSemOperacaoEHorarioVencidoBloqueiamPedido() {
        var c=cenario(ModoRota.FIXA,12);Long aluno=usuario("PASSAGEIRO").getId();var atual=rotas.consultar(c.rota());
        var d=new RotaPublicaRequest(c.turma(),atual.nome(),atual.cidade(),atual.categoria(),atual.modo(),atual.origemNome(),atual.origemLatitude(),atual.origemLongitude(),
                atual.destinoNome(),atual.destinoLatitude(),atual.destinoLongitude(),null,null,null,Set.of(DayOfWeek.MONDAY),LocalTime.MIN,null);
        rotas.atualizar(c.rota(),c.dono(),d);
        LocalDate terca=LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.TUESDAY));
        assertThrows(SolicitacaoConflictException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(terca,"IDA",c.parada(),null,null,null,null,null)));
        var todos=new RotaPublicaRequest(c.turma(),atual.nome(),atual.cidade(),atual.categoria(),atual.modo(),atual.origemNome(),atual.origemLatitude(),atual.origemLongitude(),
                atual.destinoNome(),atual.destinoLatitude(),atual.destinoLongitude(),null,null,null,EnumSet.allOf(DayOfWeek.class),LocalTime.MIN,null);
        rotas.atualizar(c.rota(),c.dono(),todos);
        assertThrows(SolicitacaoConflictException.class,()->service.solicitar(c.rota(),aluno,new EmbarqueRequest(LocalDate.now(clock),"IDA",c.parada(),null,null,null,null,null)));
    }
}
