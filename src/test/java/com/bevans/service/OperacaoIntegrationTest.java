package com.bevans.service;
import com.bevans.model.*;
import com.bevans.dto.*;
import com.bevans.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:operacao;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@Import(OperacaoIntegrationTest.Tempo.class)
class OperacaoIntegrationTest {
    static class Relogio extends Clock {
        final ZoneId zona=ZoneId.of("America/Sao_Paulo");final Instant inicial=LocalDate.now(zona).atTime(12,0).atZone(zona).toInstant();volatile Instant agora=inicial;
        public Instant instant(){return agora;}public ZoneId getZone(){return zona;}public Clock withZone(ZoneId z){return Clock.fixed(agora,z);}
    }
    @TestConfiguration static class Tempo {@Bean @Primary Clock testeClock(){return new Relogio();}}
    @org.junit.jupiter.api.BeforeEach void reiniciarRelogio(){((Relogio)clock).agora=((Relogio)clock).inicial;}
    @Autowired OperacaoService service;@Autowired ViagemService viagens;@Autowired ViagemRepository viagemRepo;
    @Autowired UsuarioRepository usuarios;@Autowired TurmaRepository turmas;@Autowired AlunoTurmaRepository vinculos;
    @Autowired EnderecoRepository enderecos;@Autowired PresencaRepository presencas;@Autowired Clock clock;
    @Autowired ExcursaoService excursoes;@Autowired ConviteExcursaoService convites;@Autowired InscricaoExcursaoService inscricoes;
    @Autowired ExecucaoExcursaoService execucoes;@Autowired RotaPublicaService publicas;@Autowired EmbarqueService embarques;
    @Autowired ColetaPrivadaService coletas;@Autowired OperacaoViagemRepository operacoes;@Autowired RegistroOperacionalRepository registros;
    @Autowired NotificacaoService notificacoes;@Autowired NotificacaoRepository notificacaoRepo;
    @Autowired OutboxNotificacaoRepository outbox;@Autowired DispositivoNotificacaoService dispositivos;
    @Autowired DispositivoNotificacaoRepository dispositivoRepo;@Autowired EntregaPushService pushes;
    @Autowired EntregaPushRepository entregaRepo;@Autowired org.springframework.transaction.PlatformTransactionManager tx;
    @Autowired org.springframework.context.ApplicationEventPublisher eventos;
    @Autowired ReservaOutboxService reservas;
    @Autowired NotificacaoInscricaoService avisosWeb;
    @Autowired PosicaoExcursaoService gpsExcursao;@Autowired PosicaoExecucaoExcursaoRepository posicoesExcursao;
    @Autowired CredencialInscricaoExcursaoRepository credenciaisInscricao;
    @Autowired PosicaoViagemService gpsViagem;@Autowired ContextoProximidadeService contextoProximidade;
    private record Cenario(Turma turma,Usuario dono,Usuario aluno){}
    private Usuario usuario(String tipo){String id=UUID.randomUUID().toString();var u=new Usuario();u.setNome(id);u.setTelefone(id);u.setEmail(id+"@test.invalid");u.setFirebaseUid(id);u.setTipo(tipo);return usuarios.save(u);}
    private Cenario cenario(){
        var d=usuario("MOTORISTA");var a=usuario("PASSAGEIRO");var t=new Turma();t.setNome("Operação");t.setMotorista(d);t.setCapacidade(12);t.setOrigemNome("Garagem");t.setOrigemLatitude(-8.0);t.setOrigemLongitude(-35.0);t.setDestinoNome("Campus");t.setDestinoLatitude(-8.3);t.setDestinoLongitude(-35.3);t=turmas.save(t);
        var v=new AlunoTurma();v.setTurma(t);v.setAluno(a);v.setStatus("APROVADO");vinculos.save(v);
        var e=new Endereco();e.setUsuario(a);e.setLatitude(-8.1);e.setLongitude(-35.1);e=enderecos.save(e);var f=new Endereco();f.setUsuario(a);f.setLatitude(-8.2);f.setLongitude(-35.2);f=enderecos.save(f);
        var p=new Presenca();p.setTurma(t);p.setUsuario(a);p.setData(LocalDate.now(clock));p.setStatus("AMBOS");p.setEnderecoIda(e);p.setEnderecoVolta(f);presencas.save(p);return new Cenario(t,d,a);
    }
    private Long iniciar(Cenario c,String s){viagens.iniciarRota(c.turma().getId(),s);return viagemRepo.findFirstByTurmaIdAndStatus(c.turma().getId(),"EM_ANDAMENTO").orElseThrow().getId();}
    private OperacaoDTO.Alteracao estado(EstadoOperacional e,long versao){return new OperacaoDTO.Alteracao(e,false,versao,null);}
    private void distribuir(){for(int i=0;i<1000;i++){Long id=reservas.reservar();if(id==null)break;notificacoes.distribuir(id);} }
    @Test void centralPersisteDeduplicaIsolaLeituraERevalidaVinculo(){
        var c=cenario();Long id=iniciar(c,"IDA");distribuir();
        var lista=notificacoes.listar(c.aluno().getFirebaseUid(),0,20);
        assertEquals(1,lista.getTotalElements());assertEquals("VIAGEM_INICIADA",lista.getContent().getFirst().tipo());
        assertEquals(1,notificacoes.naoLidas(c.aluno().getFirebaseUid()));
        Long aviso=lista.getContent().getFirst().id();var estranho=usuario("PASSAGEIRO");
        assertThrows(ResponseStatusException.class,()->notificacoes.ler(estranho.getFirebaseUid(),aviso));
        notificacoes.ler(c.aluno().getFirebaseUid(),aviso);notificacoes.ler(c.aluno().getFirebaseUid(),aviso);
        assertEquals(0,notificacoes.naoLidas(c.aluno().getFirebaseUid()));
        var r=service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));
        service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));distribuir();
        assertEquals(2,notificacoes.listar(c.aluno().getFirebaseUid(),0,20).getTotalElements());
        assertEquals(4,notificacoes.listar(c.dono().getFirebaseUid(),0,20).getTotalElements());
        var vinculo=vinculos.findByTurmaIdAndAlunoId(c.turma().getId(),c.aluno().getId()).orElseThrow();vinculo.setStatus("REMOVIDO");vinculos.save(vinculo);
        service.alterarDono(TipoOperacao.VIAGEM,id,r.id(),c.dono().getFirebaseUid(),estado(EstadoOperacional.DESEMBARCADO,r.versao()));distribuir();
        assertEquals(2,notificacoes.listar(c.aluno().getFirebaseUid(),0,20).getTotalElements());
        notificacoes.lerTodas(c.dono().getFirebaseUid());assertEquals(0,notificacoes.naoLidas(c.dono().getFirebaseUid()));
    }
    @Test void rollbackNaoPublicaEventoENovaEntregaNaoExigeFirebaseReal(){
        var c=cenario();Long id=iniciar(c,"IDA");var op=operacoes.findByTipoAndReferenciaId(TipoOperacao.VIAGEM,id).orElseThrow();
        String chave="rollback:"+UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(tx).execute(status->{eventos.publishEvent(new EventosDeViagem.Operacao(op.id,null,"VIAGEM_INICIADA",chave));status.setRollbackOnly();return null;});
        assertFalse(outbox.existsByChave(chave));
        String instalacao=UUID.randomUUID().toString();String token="token-local-de-teste-"+UUID.randomUUID();
        var dispositivo=dispositivos.registrar(c.aluno().getFirebaseUid(),new NotificacaoDTO.Dispositivo(instalacao,token,true));
        assertThrows(ResponseStatusException.class,()->dispositivos.registrar(c.dono().getFirebaseUid(),new NotificacaoDTO.Dispositivo(instalacao,token,true)));
        distribuir();var pedido=pushes.reservar();assertNotNull(pedido);
        assertFalse(pedido.toString().contains(token));
        assertEquals(PushGateway.Resultado.INDISPONIVEL,new FirebasePushGateway(false).enviar(token,pedido.notificacao(),pedido.titulo(),pedido.mensagem()));
        pushes.concluir(pedido,PushGateway.Resultado.TOKEN_INVALIDO);
        assertFalse(dispositivoRepo.findById(dispositivo.id()).orElseThrow().ativo);
        assertEquals("FALHA",entregaRepo.findById(pedido.entrega()).orElseThrow().estado);
        dispositivos.registrar(c.aluno().getFirebaseUid(),new NotificacaoDTO.Dispositivo(instalacao,token+"-novo",true));
        dispositivos.revogar(c.aluno().getFirebaseUid(),instalacao);assertFalse(dispositivoRepo.findById(dispositivo.id()).orElseThrow().ativo);
    }
    @Test void alunoConfirmaSozinhoMotoristaRepeteSemDuplicarEDesembarqueTambem(){
        var c=cenario();Long id=iniciar(c,"IDA");var antes=service.minha(id,c.aluno().getFirebaseUid());assertEquals(EstadoOperacional.AGUARDANDO,antes.estado());
        var r=service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));String autor=r.autor();
        var repetido=service.alterarDono(TipoOperacao.VIAGEM,id,r.id(),c.dono().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));assertEquals(autor,repetido.autor());assertEquals(1,service.historicoMeu(id,c.aluno().getFirebaseUid()).size());
        assertThrows(ResponseStatusException.class,()->viagens.encerrarRota(c.turma().getId()));
        r=service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.DESEMBARCADO,r.versao()));
        assertEquals(1,service.painel(TipoOperacao.VIAGEM,id,c.dono().getFirebaseUid()).contadores().desembarcados());assertEquals(0,service.painel(TipoOperacao.VIAGEM,id,c.dono().getFirebaseUid()).contadores().naVan());
        viagens.encerrarRota(c.turma().getId());assertEquals(EstadoOperacional.DESEMBARCADO,service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.DESEMBARCADO,0)).estado());
        assertThrows(ResponseStatusException.class,()->service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,2)));
    }
    @Test void motoristaCorrigeComAuditoriaEReplayAntigoNaoDesfazCorrecao(){
        var c=cenario();Long id=iniciar(c,"IDA");var r=service.painel(TipoOperacao.VIAGEM,id,c.dono().getFirebaseUid()).registros().get(0);
        assertThrows(ResponseStatusException.class,()->service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.DESEMBARCADO,0)));
        r=service.alterarDono(TipoOperacao.VIAGEM,id,r.id(),c.dono().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));Long reg=r.id();
        assertThrows(AccessDeniedException.class,()->service.alterarMinha(id,c.aluno().getFirebaseUid(),new OperacaoDTO.Alteracao(EstadoOperacional.AGUARDANDO,true,1L,"Engano")));
        var corrigido=service.alterarDono(TipoOperacao.VIAGEM,id,reg,c.dono().getFirebaseUid(),new OperacaoDTO.Alteracao(EstadoOperacional.AGUARDANDO,true,1L,"Toque acidental"));
        assertEquals(EstadoOperacional.AGUARDANDO,corrigido.estado());assertEquals(2,service.historico(TipoOperacao.VIAGEM,id,reg,c.dono().getFirebaseUid()).size());
        assertThrows(ResponseStatusException.class,()->service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0)));
    }
    @Test void confirmacoesConcorrentesDeAtoresDiferentesContamUmaVez() throws Exception {
        var c=cenario();Long id=iniciar(c,"IDA");var r=service.minha(id,c.aluno().getFirebaseUid());var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{inicio.await();return service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));});
            var b=pool.submit(()->{inicio.await();return service.alterarDono(TipoOperacao.VIAGEM,id,r.id(),c.dono().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));});inicio.countDown();
            assertEquals(a.get(20,TimeUnit.SECONDS).autor(),b.get(20,TimeUnit.SECONDS).autor());
            assertEquals(1,service.painel(TipoOperacao.VIAGEM,id,c.dono().getFirebaseUid()).contadores().naVan());assertEquals(1,service.historicoMeu(id,c.aluno().getFirebaseUid()).size());
        }finally{inicio.countDown();pool.shutdownNow();}
    }
    @Test void isolamentoDeTurmasViagensESentidosEContadoresPersistidos(){
        var c=cenario();var outra=cenario();Long id=iniciar(c,"IDA"),alheia=iniciar(outra,"IDA");var r=service.minha(id,c.aluno().getFirebaseUid());
        assertThrows(AccessDeniedException.class,()->service.minha(id,outra.aluno().getFirebaseUid()));assertThrows(AccessDeniedException.class,()->service.painel(TipoOperacao.VIAGEM,id,outra.dono().getFirebaseUid()));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.alterarDono(TipoOperacao.VIAGEM,alheia,r.id(),outra.dono().getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0))).getStatusCode().value());
        service.alterarMinha(id,c.aluno().getFirebaseUid(),estado(EstadoOperacional.NAO_EMBARCOU,0));viagens.encerrarRota(c.turma().getId());Long volta=iniciar(c,"VOLTA");
        assertNotEquals(id,volta);assertEquals(EstadoOperacional.AGUARDANDO,service.minha(volta,c.aluno().getFirebaseUid()).estado());assertEquals(1,service.painel(TipoOperacao.VIAGEM,id,c.dono().getFirebaseUid()).contadores().naoEmbarcaram());
    }
    @Test void inscricaoExcursionistaUsaCredencialPropriaEOuOrganizadorSemAlunoTurma(){
        var dono=usuario("MOTORISTA");long matriculas=vinculos.count();var e=excursoes.criar(dono.getFirebaseUid(),new ExcursaoRequest("Evento","Destino",-8.3,-35.3,"Origem",-8.0,-35.0,clock.instant().plusSeconds(86400),null,12,null));e=excursoes.publicar(e.id(),dono.getFirebaseUid(),e.versao());
        var convite=convites.emitir(e.id(),dono.getFirebaseUid(),clock.instant().plusSeconds(3600));String token=inscricoes.emitirCredencial(convite.token()).token();var i=inscricoes.solicitar(convite.token(),token,new InscricaoExcursaoRequest("Sem celular","contato@test.invalid",null,"Pin",-8.1,-35.1,true,true));inscricoes.confirmar(e.id(),i.id(),dono.getFirebaseUid());
        var x=execucoes.iniciar(e.id(),dono.getFirebaseUid(),"IDA",false);var r=service.minhaWeb(x.id(),token);assertNull(r.alunoId());assertEquals(i.id(),r.inscricaoId());
        var amostra=new PosicaoRequest("exc-inicial",-8.1,-35.1,clock.instant(),5.0,null);
        assertEquals("SEM_LOCALIZACAO",gpsExcursao.consultarParticipante(x.id(),token).estado());
        assertThrows(ResponseStatusException.class,()->gpsExcursao.consultarParticipante(x.id(),convite.token()));
        assertThrows(AccessDeniedException.class,()->gpsExcursao.enviar(x.id(),usuario("MOTORISTA").getFirebaseUid(),amostra));
        gpsExcursao.enviar(x.id(),dono.getFirebaseUid(),amostra);
        var posicao=gpsExcursao.consultarParticipante(x.id(),token);assertEquals("ATUALIZADA",posicao.estado());assertEquals(-8.1,posicao.latitude());
        assertEquals(posicao.recebidoEm(),gpsExcursao.enviar(x.id(),dono.getFirebaseUid(),amostra).recebidoEm());
        assertTrue(posicoesExcursao.findById(x.id()).isPresent());
        assertThrows(ResponseStatusException.class,()->service.minhaWeb(x.id(),convite.token()));
        distribuir();assertEquals(3,avisosWeb.listar(token,0,20).getTotalElements());
        assertThrows(ResponseStatusException.class,()->avisosWeb.listar(convite.token(),0,20));
        var aviso=avisosWeb.listar(token,0,20).getContent().getFirst();avisosWeb.ler(token,aviso.id());assertNotNull(avisosWeb.listar(token,0,20).getContent().getFirst().lidaEm());
        r=service.alterarDono(TipoOperacao.EXCURSAO,x.id(),r.id(),dono.getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));
        assertEquals("MOTORISTA",r.origem());service.alterarWeb(x.id(),token,estado(EstadoOperacional.DESEMBARCADO,r.versao()));execucoes.finalizar(e.id(),x.id(),dono.getFirebaseUid());assertEquals(matriculas,vinculos.count());
        assertNull(posicoesExcursao.findById(x.id()).orElseThrow().getLatitude());
        assertEquals("FINALIZADA",gpsExcursao.consultarParticipante(x.id(),token).estado());
        assertNull(gpsExcursao.consultarParticipante(x.id(),token).latitude());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->gpsExcursao.enviar(x.id(),dono.getFirebaseUid(),amostra)).getStatusCode().value());
    }
    @Test void publicasFixaEFlexivelSoIncluemPedidosAprovados(){
        for(ModoRota modo:ModoRota.values()){
            var c=cenario();var r=publicas.criar(c.dono().getId(),new RotaPublicaRequest(c.turma().getId(),"Pública","Caruaru",CategoriaRota.OUTROS,modo,"Origem",-8.0,-35.0,"Destino",-8.3,-35.3,null,null,null,EnumSet.allOf(DayOfWeek.class),LocalTime.of(18,0),null));publicas.publicar(r.id(),c.dono().getId(),true);
            var p=publicas.salvarParadas(r.id(),c.dono().getId(),List.of(new ParadaPublicaDTO(null,"Praça",-8.1,-35.1,null))).paradas().get(0);
            var pedido=embarques.solicitar(r.id(),c.aluno().getId(),new EmbarqueRequest(LocalDate.now(clock),"IDA",p.id(),null,null,null,null,null));embarques.analisar(pedido.id(),c.dono().getId(),StatusEmbarque.APROVADO);
            var pendente=usuario("PASSAGEIRO");embarques.solicitar(r.id(),pendente.getId(),new EmbarqueRequest(LocalDate.now(clock),"IDA",p.id(),null,null,null,null,null));
            Long id=iniciar(c,"IDA");var painel=service.painel(TipoOperacao.VIAGEM,id,c.dono().getFirebaseUid());assertEquals(1,painel.contadores().esperados());assertEquals(p.id(),painel.registros().get(0).paradaId());
            assertThrows(AccessDeniedException.class,()->service.minha(id,pendente.getFirebaseUid()));
            gpsViagem.enviar(id,c.dono().getFirebaseUid(),new PosicaoRequest("publica",-8.1,-35.1,clock.instant(),5.0,null));distribuir();
            assertTrue(notificacoes.listar(c.aluno().getFirebaseUid(),0,20).stream().anyMatch(n->n.tipo().equals("VAN_CHEGOU")));
            assertTrue(notificacoes.listar(pendente.getFirebaseUid(),0,20).isEmpty());
        }
    }
    @Test void cancelamentoLimpaGpsEInscricaoAusenteOuExpiradaNaoPodeRastrear()throws Exception{
        var dono=usuario("MOTORISTA");var e=excursoes.criar(dono.getFirebaseUid(),new ExcursaoRequest("GPS","Destino",-8.3,-35.3,"Origem",-8.0,-35.0,clock.instant().plusSeconds(86400),null,12,null));e=excursoes.publicar(e.id(),dono.getFirebaseUid(),e.versao());
        var convite=convites.emitir(e.id(),dono.getFirebaseUid(),clock.instant().plusSeconds(3600));String token=inscricoes.emitirCredencial(convite.token()).token();String naoInscrito=inscricoes.emitirCredencial(convite.token()).token();
        var i=inscricoes.solicitar(convite.token(),token,new InscricaoExcursaoRequest("GPS","gps@test.invalid",null,"Pin",-8.1,-35.1,true,true));inscricoes.confirmar(e.id(),i.id(),dono.getFirebaseUid());
        var x=execucoes.iniciar(e.id(),dono.getFirebaseUid(),"IDA",false);var amostra=new PosicaoRequest("concorrente",-8.1,-35.1,clock.instant(),5.0,null);
        var pool=Executors.newFixedThreadPool(2);try{
            var a=pool.submit(()->gpsExcursao.enviar(x.id(),dono.getFirebaseUid(),amostra));var b=pool.submit(()->gpsExcursao.enviar(x.id(),dono.getFirebaseUid(),amostra));
            assertEquals(a.get(20,TimeUnit.SECONDS).recebidoEm(),b.get(20,TimeUnit.SECONDS).recebidoEm());
        }finally{pool.shutdownNow();}
        assertThrows(ResponseStatusException.class,()->gpsExcursao.consultarParticipante(x.id(),naoInscrito));
        var cred=credenciaisInscricao.findByTokenHash(TokensExcursao.hash(token)).orElseThrow();cred.prolongar(clock.instant().minusSeconds(1));credenciaisInscricao.save(cred);
        assertThrows(ResponseStatusException.class,()->gpsExcursao.consultarParticipante(x.id(),token));
        excursoes.cancelar(e.id(),dono.getFirebaseUid());
        assertNull(posicoesExcursao.findById(x.id()).orElseThrow().getLatitude());assertEquals("FINALIZADA",gpsExcursao.consultar(x.id(),dono.getFirebaseUid()).estado());
    }
    @Test void proximidadeRespeitaOrdemGpsRecenteDeduplicacaoEExpiraFilaEPush(){
        var c=cenario();var outro=usuario("PASSAGEIRO");var v=new AlunoTurma();v.setTurma(c.turma());v.setAluno(outro);v.setStatus("APROVADO");vinculos.save(v);
        var endereco=new Endereco();endereco.setUsuario(outro);endereco.setLatitude(-8.12);endereco.setLongitude(-35.12);endereco=enderecos.save(endereco);
        var p=new Presenca();p.setTurma(c.turma());p.setUsuario(outro);p.setData(LocalDate.now(clock));p.setStatus("IDA");p.setEnderecoIda(endereco);presencas.save(p);
        Long id=iniciar(c,"IDA");var op=operacoes.findByTipoAndReferenciaId(TipoOperacao.VIAGEM,id).orElseThrow();var rs=registros.findByOperacaoIdOrderByOrdemAscIdAsc(op.id);var proximo=rs.getFirst();var seguinte=rs.getLast();
        distribuir();assertTrue(contextoProximidade.alertas(op.id).isEmpty());
        var dispositivo=dispositivos.registrar(proximo.aluno.getFirebaseUid(),new NotificacaoDTO.Dispositivo(UUID.randomUUID().toString(),"token-local-"+UUID.randomUUID(),true));
        var amostra=new PosicaoRequest("longe",proximo.latitude+0.003,proximo.longitude,clock.instant(),5.0,null);
        gpsViagem.enviar(id,c.dono().getFirebaseUid(),amostra);long quantidade=outbox.count();gpsViagem.enviar(id,c.dono().getFirebaseUid(),amostra);assertEquals(quantidade,outbox.count());
        assertEquals(Map.of(proximo.id,"VAN_APROXIMANDO"),contextoProximidade.alertas(op.id));distribuir();
        assertTrue(notificacoes.listar(proximo.aluno.getFirebaseUid(),0,50).stream().anyMatch(n->n.tipo().equals("VAN_APROXIMANDO")));
        assertFalse(notificacoes.listar(seguinte.aluno.getFirebaseUid(),0,50).stream().anyMatch(n->ContextoProximidadeService.TIPOS.contains(n.tipo())));
        ((Relogio)clock).agora=clock.instant().plusSeconds(5);gpsViagem.enviar(id,c.dono().getFirebaseUid(),new PosicaoRequest("perto",proximo.latitude+0.001,proximo.longitude,clock.instant(),5.0,null));distribuir();
        assertTrue(notificacoes.listar(proximo.aluno.getFirebaseUid(),0,50).stream().anyMatch(n->n.tipo().equals("VOCE_E_PROXIMO")));
        ((Relogio)clock).agora=clock.instant().plusSeconds(5);gpsViagem.enviar(id,c.dono().getFirebaseUid(),new PosicaoRequest("chegou",proximo.latitude,proximo.longitude,clock.instant(),5.0,null));
        assertEquals(EstadoOperacional.AGUARDANDO,registros.findById(proximo.id).orElseThrow().estado);
        ((Relogio)clock).agora=clock.instant().plusSeconds(31);assertTrue(contextoProximidade.alertas(op.id).isEmpty());distribuir();
        assertFalse(notificacoes.listar(proximo.aluno.getFirebaseUid(),0,50).stream().anyMatch(n->n.tipo().equals("VAN_CHEGOU")));
        assertNull(pushes.reservar());assertTrue(entregaRepo.findAll().stream().filter(x->x.dispositivo.id.equals(dispositivo.id())).anyMatch(x->x.estado.equals("CANCELADA")));
        gpsViagem.enviar(id,c.dono().getFirebaseUid(),new PosicaoRequest("imprecisa",proximo.latitude,proximo.longitude,clock.instant(),100.0,null));assertTrue(contextoProximidade.alertas(op.id).isEmpty());
        service.alterarMinha(id,proximo.aluno.getFirebaseUid(),estado(EstadoOperacional.EMBARCADO,0));
        ((Relogio)clock).agora=clock.instant().plusSeconds(5);gpsViagem.enviar(id,c.dono().getFirebaseUid(),new PosicaoRequest("seguinte",seguinte.latitude,seguinte.longitude,clock.instant(),5.0,null));
        assertEquals(Map.of(seguinte.id,"VAN_CHEGOU"),contextoProximidade.alertas(op.id));
        assertEquals(EstadoOperacional.EMBARCADO,registros.findById(proximo.id).orElseThrow().estado);
        dispositivos.revogar(proximo.aluno.getFirebaseUid(),dispositivo.instalacaoId());
    }
    @Test void coletaPrivadaUsaParadaEfetivaNoSnapshotOperacional(){
        var c=cenario();var p=presencas.findByUsuarioIdAndTurmaIdAndData(c.aluno().getId(),c.turma().getId(),LocalDate.now(clock)).orElseThrow();p.setStatus("NAO_VOU");presencas.save(p);
        var cfg=coletas.configurar(c.turma().getId(),"IDA",c.dono().getFirebaseUid(),new ColetaPrivadaRequest(ModoColetaPrivada.PARADAS_DEFINIDAS,0L,List.of(new ColetaPrivadaRequest.Parada(null,"Praça escolhida",-8.15,-35.15,null,true))));
        coletas.escolher(c.turma().getId(),"IDA",c.aluno().getFirebaseUid(),null,cfg.paradas().get(0).id(),cfg.versao());p.setStatus("IDA");presencas.save(p);
        Long id=iniciar(c,"IDA");var r=service.minha(id,c.aluno().getFirebaseUid());assertEquals(cfg.paradas().get(0).id(),r.paradaId());assertEquals(-8.15,registros.findById(r.id()).orElseThrow().latitude);
        gpsViagem.enviar(id,c.dono().getFirebaseUid(),new PosicaoRequest("escolhida",-8.15,-35.15,clock.instant(),5.0,null));distribuir();
        assertTrue(notificacoes.listar(c.aluno().getFirebaseUid(),0,20).stream().anyMatch(n->n.tipo().equals("VAN_CHEGOU")));
    }
    @Test void falhaDeSnapshotDesfazInicioInteiroSemOperacaoParcial(){
        var c=cenario();var t=c.turma();t.setOrigemLatitude(null);t.setOrigemLongitude(null);turmas.save(t);long antes=operacoes.count();
        assertThrows(IllegalArgumentException.class,()->viagens.iniciarRota(t.getId(),"IDA"));
        assertTrue(viagemRepo.findFirstByTurmaIdAndStatus(t.getId(),"EM_ANDAMENTO").isEmpty());assertEquals(antes,operacoes.count());
    }
}
