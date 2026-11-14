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
@Import({ExcursaoService.class,ConviteExcursaoService.class,InscricaoExcursaoService.class,ConviteExcursaoIntegrationTest.Tempo.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class InscricaoExcursaoIntegrationTest {
    @Autowired ExcursaoService excursoes;@Autowired ConviteExcursaoService convites;@Autowired InscricaoExcursaoService service;
    @Autowired UsuarioRepository usuarios;@Autowired InscricaoExcursaoRepository inscricoes;@Autowired Clock clock;
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
}
