package com.bevans.repository;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({ExcursaoService.class,ConviteExcursaoService.class,InscricaoExcursaoService.class,RoteiroExcursaoService.class,ExecucaoExcursaoService.class,ConviteExcursaoIntegrationTest.Tempo.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class InscricaoExcursaoIntegrationTest {
    @Autowired ExcursaoService excursoes;@Autowired ConviteExcursaoService convites;@Autowired InscricaoExcursaoService service;
    @Autowired UsuarioRepository usuarios;@Autowired InscricaoExcursaoRepository inscricoes;@Autowired Clock clock;
    @Autowired RoteiroExcursaoService roteiros;
    @Autowired ExecucaoExcursaoService execucoes;
    private record Cenario(Long id,String uid,ConviteEmitidoDTO convite){}
    @BeforeEach void tempo(){((ConviteExcursaoIntegrationTest.Relogio)clock).agora=Instant.parse("2030-01-01T12:00:00Z");}
    private String motorista(){String uid=UUID.randomUUID().toString();var u=new Usuario();u.setNome(uid);u.setTelefone(uid);u.setEmail(uid+"@test.invalid");u.setFirebaseUid(uid);u.setTipo("MOTORISTA");usuarios.save(u);return uid;}
    private Cenario cenario(int cap,ModoColetaExcursao modo){
        String uid=motorista();var e=excursoes.criar(uid,new ExcursaoRequest("Evento","Destino",-8.3,-35.3,"Origem",-8.0,-35.0,clock.instant().plusSeconds(86400),null,cap,null));
        var pontos=new ArrayList<ParadaExcursaoRequest>();
        if(modo!=ModoColetaExcursao.PORTA_A_PORTA)pontos.add(new ParadaExcursaoRequest(null,"Praça A",-8.1,-35.1,null,null,true));
        if(modo==ModoColetaExcursao.MULTIPLAS_PARADAS)pontos.add(new ParadaExcursaoRequest(null,"Praça B",-8.2,-35.2,null,null,true));
        e=excursoes.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(modo,pontos,e.versao()));e=excursoes.publicar(e.id(),uid,e.versao());
        return new Cenario(e.id(),uid,convites.emitir(e.id(),uid,clock.instant().plusSeconds(7200)));
    }
    private String chave(Cenario c){return service.emitirCredencial(c.convite().token()).token();}
    private InscricaoExcursaoRequest porta(String nome){return new InscricaoExcursaoRequest(nome,nome+"@test.invalid",null,"Meu pin",-8.12,-35.12,true,true);}
    private InscricaoExcursaoRequest parada(String nome,Long id){return new InscricaoExcursaoRequest(nome,nome+"@test.invalid",id,null,null,null,null,true);}
    private ExcursaoRequest dados(ExcursaoDTO e,int cap,Instant partida){return new ExcursaoRequest(e.titulo(),e.destinoNome(),e.destinoLatitude(),e.destinoLongitude(),e.origemNome(),e.origemLatitude(),e.origemLongitude(),partida,e.retornoEm(),cap,e.versao());}
    @Test void reservaConfirmaEConsultaComCredencialIndependenteDoConvite(){
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);String token=chave(c);var d=porta("Ana");var i=service.solicitar(c.convite().token(),token,d);
        assertEquals(StatusInscricaoExcursao.SOLICITADA,i.estado());assertEquals(i.id(),service.solicitar(c.convite().token(),token,d).id());
        assertThrows(ResponseStatusException.class,()->service.solicitar(c.convite().token(),token,porta("Outra")));
        assertEquals(StatusInscricaoExcursao.CONFIRMADA,service.confirmar(c.id(),i.id(),c.uid()).estado());
        assertEquals(i.id(),service.confirmar(c.id(),i.id(),c.uid()).id());convites.revogar(c.id(),c.convite().convite().id(),c.uid());
        assertEquals(StatusInscricaoExcursao.CONFIRMADA,service.consultar(token).estado());assertTrue(convites.listar(c.id(),c.uid()).get(0).revogadoEm()!=null);
        assertEquals(1,inscricoes.ocupacao(c.id(),clock.instant()));
        assertEquals(1,service.vagas(c.id(),c.uid()).confirmadas());assertEquals(11,service.vagas(c.id(),c.uid()).disponiveis());
    }
    @Test void inscricaoFuncionaNosModosDeParadaSemEnderecoResidencial(){
        var unica=cenario(12,ModoColetaExcursao.PARADA_UNICA);var a=service.solicitar(unica.convite().token(),chave(unica),parada("Bia",null));
        assertEquals("Praça A",a.nomePonto());assertNotNull(a.paradaId());
        var multipla=cenario(12,ModoColetaExcursao.MULTIPLAS_PARADAS);Long id=excursoes.consultar(multipla.id(),multipla.uid()).paradas().get(1).id();
        var b=service.solicitar(multipla.convite().token(),chave(multipla),parada("Caio",id));assertEquals("Praça B",b.nomePonto());
        assertThrows(IllegalArgumentException.class,()->service.solicitar(multipla.convite().token(),chave(multipla),parada("Dani",a.paradaId())));
        assertThrows(IllegalArgumentException.class,()->service.solicitar(unica.convite().token(),chave(unica),porta("Eva")));
    }
    @Test void concorrenciaPelaUltimaVagaNaoExcedeCapacidade() throws Exception {
        var c=cenario(1,ModoColetaExcursao.PORTA_A_PORTA);String a=chave(c),b=chave(c);var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            java.util.function.Function<String,Callable<Integer>> tarefa=t->()->{inicio.await();try{service.solicitar(c.convite().token(),t,porta(t.substring(0,8)));return 200;}catch(ResponseStatusException ex){return ex.getStatusCode().value();}};
            var x=pool.submit(tarefa.apply(a));var y=pool.submit(tarefa.apply(b));inicio.countDown();var codigos=List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS));
            assertTrue(codigos.contains(200));assertTrue(codigos.contains(409));assertEquals(1,inscricoes.ocupacao(c.id(),clock.instant()));
        }finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void reservaExpiraELiberaVagaSemApagarHistorico(){
        var c=cenario(1,ModoColetaExcursao.PORTA_A_PORTA);String a=chave(c);var i=service.solicitar(c.convite().token(),a,porta("Ana"));
        ((ConviteExcursaoIntegrationTest.Relogio)clock).agora=clock.instant().plusSeconds(901);
        assertEquals(StatusInscricaoExcursao.EXPIRADA,service.consultar(a).estado());
        assertThrows(ResponseStatusException.class,()->service.confirmar(c.id(),i.id(),c.uid()));
        var nova=service.solicitar(c.convite().token(),chave(c),porta("Ana"));assertNotEquals(i.id(),nova.id());assertEquals(2,service.listar(c.id(),c.uid(),0,20).total());
    }
    @Test void protegeEdicoesCapacidadeModoEParadasComReservaAtiva(){
        var c=cenario(2,ModoColetaExcursao.PARADA_UNICA);String a=chave(c),b=chave(c);
        service.solicitar(c.convite().token(),a,parada("Ana",null));service.solicitar(c.convite().token(),b,parada("Bia",null));var e=excursoes.consultar(c.id(),c.uid());
        assertThrows(ResponseStatusException.class,()->excursoes.atualizar(c.id(),c.uid(),dados(e,1,e.partidaEm())));
        assertThrows(ResponseStatusException.class,()->excursoes.atualizar(c.id(),c.uid(),dados(e,2,e.partidaEm().plusSeconds(60))));
        assertThrows(ResponseStatusException.class,()->excursoes.configurarColeta(c.id(),c.uid(),new ColetaExcursaoRequest(ModoColetaExcursao.PORTA_A_PORTA,List.of(),e.versao())));
        service.cancelar(a);assertEquals(StatusInscricaoExcursao.CANCELADA,service.cancelar(a).estado());
        assertEquals(1,excursoes.atualizar(c.id(),c.uid(),dados(e,1,e.partidaEm())).capacidade());
    }
    @Test void isolamentoCredenciaisDuplicidadeECancelamentoDaExcursao(){
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);var outro=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);
        String a=chave(c),semInscricao=chave(c);var i=service.solicitar(c.convite().token(),a,porta("Ana"));
        assertThrows(ResponseStatusException.class,()->service.solicitar(c.convite().token(),semInscricao,porta("Ana")));
        assertThrows(ResponseStatusException.class,()->service.consultar(semInscricao));
        assertThrows(ResponseStatusException.class,()->service.solicitar(outro.convite().token(),chave(c),porta("Bia")));
        assertThrows(AccessDeniedException.class,()->service.confirmar(c.id(),i.id(),outro.uid()));
        assertThrows(AccessDeniedException.class,()->service.listar(c.id(),outro.uid(),0,20));
        assertThrows(ResponseStatusException.class,()->service.confirmar(outro.id(),i.id(),outro.uid()));
        excursoes.cancelar(c.id(),c.uid());assertEquals(StatusInscricaoExcursao.CANCELADA,service.consultar(a).estado());assertEquals(0,inscricoes.ocupacao(c.id(),clock.instant()));
    }
    @Test void tokenExpiradoOuPinNaoConfirmadoNaoCriaInscricao(){
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);String token=chave(c);
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.convite().token(),token,new InscricaoExcursaoRequest("Ana","ana@test.invalid",null,"Rua",-8.0,-35.0,false,true)));
        assertThrows(IllegalArgumentException.class,()->service.solicitar(c.convite().token(),token,new InscricaoExcursaoRequest("Ana","ana@test.invalid",null,"Rua",-8.0,-35.0,true,false)));
        ((ConviteExcursaoIntegrationTest.Relogio)clock).agora=clock.instant().plusSeconds(1801);
        assertThrows(ResponseStatusException.class,()->service.solicitar(c.convite().token(),token,porta("Ana")));assertEquals(0,inscricoes.ocupacao(c.id(),clock.instant()));
    }
    @Test void mesmaCredencialEmRequisicoesSimultaneasRetornaUmaInscricao() throws Exception {
        var c=cenario(2,ModoColetaExcursao.PORTA_A_PORTA);String token=chave(c);var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Long> acao=()->{inicio.await();return service.solicitar(c.convite().token(),token,porta("Ana")).id();};
            var a=pool.submit(acao);var b=pool.submit(acao);inicio.countDown();assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,inscricoes.ocupacao(c.id(),clock.instant()));
        }finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void confirmarECancelarConcorrentementeNaoRessuscitaReserva() throws Exception {
        var c=cenario(1,ModoColetaExcursao.PORTA_A_PORTA);String token=chave(c);var i=service.solicitar(c.convite().token(),token,porta("Ana"));
        var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{inicio.await();service.cancelar(token);return true;});
            var b=pool.submit(()->{inicio.await();try{service.confirmar(c.id(),i.id(),c.uid());}catch(ResponseStatusException ex){assertEquals(409,ex.getStatusCode().value());}return true;});
            inicio.countDown();a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
            assertEquals(StatusInscricaoExcursao.CANCELADA,service.consultar(token).estado());assertEquals(0,inscricoes.ocupacao(c.id(),clock.instant()));
        }finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void roteiroDosTresModosIncluiSomenteConfirmadosEDestinoFinal(){
        for(var modo:ModoColetaExcursao.values()){
            var c=cenario(12,modo);Long paradaId=modo==ModoColetaExcursao.PORTA_A_PORTA?null:excursoes.consultar(c.id(),c.uid()).paradas().get(0).id();
            var d=modo==ModoColetaExcursao.PORTA_A_PORTA?porta("Ana"):parada("Ana",paradaId);
            var i=service.solicitar(c.convite().token(),chave(c),d);service.confirmar(c.id(),i.id(),c.uid());
            service.solicitar(c.convite().token(),chave(c),modo==ModoColetaExcursao.PORTA_A_PORTA?porta("Pendente"):parada("Pendente",paradaId));
            var pontos=roteiros.planejamento(c.id(),c.uid(),"IDA",true);
            assertEquals("ORIGEM",pontos.get(0).tipo());assertEquals("Destino",pontos.get(pontos.size()-1).nome());
            assertEquals(List.of(i.id()),pontos.stream().flatMap(p->p.participantes().stream()).map(PontoRoteiroExcursaoDTO.Participante::inscricaoId).toList());
            assertThrows(AccessDeniedException.class,()->roteiros.planejamento(c.id(),motorista(),"IDA",false));
        }
    }
    @Test void ordemFixaEVoltaPreservamExtremosSemAlterarCadastro(){
        var c=cenario(12,ModoColetaExcursao.MULTIPLAS_PARADAS);var e=excursoes.consultar(c.id(),c.uid());
        var d=new ExcursaoRequest(e.titulo(),e.destinoNome(),e.destinoLatitude(),e.destinoLongitude(),e.origemNome(),e.origemLatitude(),e.origemLongitude(),e.partidaEm(),e.partidaEm().plusSeconds(3600),e.capacidade(),e.versao());
        excursoes.atualizar(c.id(),c.uid(),d);
        for(int n=0;n<2;n++){var i=service.solicitar(c.convite().token(),chave(c),parada("Pessoa"+n,e.paradas().get(n).id()));service.confirmar(c.id(),i.id(),c.uid());}
        var ida=roteiros.planejamento(c.id(),c.uid(),"IDA",false);assertEquals(List.of("Origem","Praça A","Praça B","Destino"),ida.stream().map(PontoRoteiroExcursaoDTO::nome).toList());
        var volta=roteiros.planejamento(c.id(),c.uid(),"VOLTA",false);assertEquals(List.of("Destino","Praça B","Praça A","Origem"),volta.stream().map(PontoRoteiroExcursaoDTO::nome).toList());
        assertEquals("DESEMBARQUE",volta.get(1).tipo());roteiros.planejamento(c.id(),c.uid(),"IDA",true);
        assertEquals(e.paradas(),excursoes.consultar(c.id(),c.uid()).paradas());
        excursoes.cancelar(c.id(),c.uid());assertThrows(ResponseStatusException.class,()->roteiros.planejamento(c.id(),c.uid(),"IDA",false));
    }
    @Test void naoInventaOrigemNemRetornoERecusaPlanejarDepoisDaPartida(){
        String uid=motorista();var e=excursoes.criar(uid,new ExcursaoRequest("Evento","Destino",-8.3,-35.3,null,null,null,clock.instant().plusSeconds(3600),null,12,null));
        e=excursoes.publicar(e.id(),uid,e.versao());Long id=e.id();
        assertEquals(List.of("DESTINO"),roteiros.planejamento(id,uid,"IDA",false).stream().map(PontoRoteiroExcursaoDTO::tipo).toList());
        assertThrows(IllegalArgumentException.class,()->roteiros.planejamento(id,uid,"IDA",true));
        assertThrows(IllegalArgumentException.class,()->roteiros.planejamento(id,uid,"VOLTA",false));
        ((ConviteExcursaoIntegrationTest.Relogio)clock).agora=clock.instant().plusSeconds(3601);
        assertThrows(ResponseStatusException.class,()->roteiros.planejamento(id,uid,"IDA",false));
    }
    @Test void iniciarCongelaRoteiroInscricoesEEdicoesEFinalizarConcluiExcursao(){
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);String token=chave(c);var i=service.solicitar(c.convite().token(),token,porta("Ana"));service.confirmar(c.id(),i.id(),c.uid());
        String pendente=chave(c);service.solicitar(c.convite().token(),pendente,porta("Bia"));
        var x=execucoes.iniciar(c.id(),c.uid(),"IDA",true);assertEquals(EstadoExecucaoExcursao.EM_ANDAMENTO,x.estado());
        assertEquals(List.of(i.id()),x.pontos().stream().flatMap(p->p.participantes().stream()).map(PontoRoteiroExcursaoDTO.Participante::inscricaoId).toList());
        assertEquals(x.id(),execucoes.iniciar(c.id(),c.uid(),"IDA",false).id());assertEquals(x.pontos(),execucoes.listar(c.id(),c.uid()).get(0).pontos());
        assertThrows(ResponseStatusException.class,()->service.cancelar(token));
        assertThrows(ResponseStatusException.class,()->service.emitirCredencial(c.convite().token()));
        var e=excursoes.consultar(c.id(),c.uid());assertThrows(ResponseStatusException.class,()->excursoes.atualizar(c.id(),c.uid(),dados(e,20,e.partidaEm())));
        assertThrows(ResponseStatusException.class,()->roteiros.planejamento(c.id(),c.uid(),"IDA",true));
        assertEquals(EstadoExecucaoExcursao.FINALIZADA,execucoes.finalizar(c.id(),x.id(),c.uid()).estado());
        assertEquals(EstadoExcursao.CONCLUIDA,excursoes.consultar(c.id(),c.uid()).estado());
        assertEquals(x.id(),execucoes.finalizar(c.id(),x.id(),c.uid()).id());
        assertThrows(ResponseStatusException.class,()->execucoes.iniciar(c.id(),c.uid(),"IDA",false));
        assertThrows(AccessDeniedException.class,()->execucoes.listar(c.id(),motorista()));
    }
    @Test void idaEVoltaTemExecucoesDistintasComExtremosPreservados(){
        var c=cenario(12,ModoColetaExcursao.PARADA_UNICA);var e=excursoes.consultar(c.id(),c.uid());
        excursoes.atualizar(c.id(),c.uid(),new ExcursaoRequest(e.titulo(),e.destinoNome(),e.destinoLatitude(),e.destinoLongitude(),e.origemNome(),e.origemLatitude(),e.origemLongitude(),e.partidaEm(),e.partidaEm().plusSeconds(3600),12,e.versao()));
        var i=service.solicitar(c.convite().token(),chave(c),parada("Ana",null));service.confirmar(c.id(),i.id(),c.uid());
        assertThrows(ResponseStatusException.class,()->execucoes.iniciar(c.id(),c.uid(),"VOLTA",false));
        var ida=execucoes.iniciar(c.id(),c.uid(),"IDA",false);
        assertThrows(ResponseStatusException.class,()->execucoes.iniciar(c.id(),c.uid(),"VOLTA",false));execucoes.finalizar(c.id(),ida.id(),c.uid());
        assertEquals(EstadoExcursao.PUBLICADA,excursoes.consultar(c.id(),c.uid()).estado());
        var volta=execucoes.iniciar(c.id(),c.uid(),"VOLTA",false);assertNotEquals(ida.id(),volta.id());
        assertEquals("Destino",volta.pontos().get(0).nome());assertEquals("Origem",volta.pontos().get(volta.pontos().size()-1).nome());
        execucoes.finalizar(c.id(),volta.id(),c.uid());assertEquals(EstadoExcursao.CONCLUIDA,excursoes.consultar(c.id(),c.uid()).estado());
    }
    @Test void iniciosSimultaneosRetornamUmaUnicaExecucaoPersistida() throws Exception {
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Long> acao=()->{inicio.await();return execucoes.iniciar(c.id(),c.uid(),"IDA",false).id();};
            var a=pool.submit(acao);var b=pool.submit(acao);inicio.countDown();assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(1,execucoes.listar(c.id(),c.uid()).size());
        }finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void cancelarExcursaoCancelaExecucaoSemApagarSnapshot(){
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);var x=execucoes.iniciar(c.id(),c.uid(),"IDA",false);
        var outro=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);
        assertThrows(AccessDeniedException.class,()->execucoes.iniciar(c.id(),outro.uid(),"IDA",false));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->execucoes.finalizar(outro.id(),x.id(),outro.uid())).getStatusCode().value());
        excursoes.cancelar(c.id(),c.uid());
        var salva=execucoes.listar(c.id(),c.uid()).get(0);assertEquals(EstadoExecucaoExcursao.CANCELADA,salva.estado());assertEquals(x.pontos(),salva.pontos());
        assertThrows(ResponseStatusException.class,()->execucoes.finalizar(c.id(),x.id(),c.uid()));
    }
    @Test void inicioComAtrasoNaoDependeDaPreviaAntesDoHorario(){
        var c=cenario(12,ModoColetaExcursao.PORTA_A_PORTA);((ConviteExcursaoIntegrationTest.Relogio)clock).agora=clock.instant().plusSeconds(86500);
        assertThrows(ResponseStatusException.class,()->roteiros.planejamento(c.id(),c.uid(),"IDA",false));
        assertEquals(EstadoExecucaoExcursao.EM_ANDAMENTO,execucoes.iniciar(c.id(),c.uid(),"IDA",false).estado());
    }
}
