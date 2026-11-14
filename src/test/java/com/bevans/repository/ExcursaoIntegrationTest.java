package com.bevans.repository;
import com.bevans.config.TempoConfig;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.ExcursaoService;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({ExcursaoService.class,TempoConfig.class}) @Transactional(propagation=Propagation.NOT_SUPPORTED)
class ExcursaoIntegrationTest {
    @Autowired ExcursaoService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired TurmaRepository turmas;
    @Autowired RotaPublicaRepository rotas;
    @Autowired Clock clock;
    @Autowired ParadaExcursaoRepository paradas;
    private String usuario(String tipo){String uid=UUID.randomUUID().toString();Usuario u=new Usuario();u.setNome(uid);u.setEmail(uid+"@test.invalid");u.setTelefone(uid);u.setFirebaseUid(uid);u.setTipo(tipo);usuarios.save(u);return uid;}
    private ExcursaoRequest dados(int capacidade,Long versao){return new ExcursaoRequest("Evento","Centro de eventos",-8.1,-35.1,null,null,null,clock.instant().plusSeconds(86400),null,capacidade,versao);}
    @Test void criaEditaPublicaECancelaSemCriarTurmaOuRota(){
        String uid=usuario("MOTORISTA");long t=turmas.count(),r=rotas.count();
        var e=service.criar(uid,dados(12,null));assertEquals(EstadoExcursao.RASCUNHO,e.estado());assertNull(e.origemLatitude());
        e=service.atualizar(e.id(),uid,dados(18,e.versao()));assertEquals(18,e.capacidade());
        e=service.publicar(e.id(),uid,e.versao());assertEquals(EstadoExcursao.PUBLICADA,e.estado());
        assertEquals(EstadoExcursao.CANCELADA,service.cancelar(e.id(),uid).estado());
        assertEquals(EstadoExcursao.CANCELADA,service.cancelar(e.id(),uid).estado());
        assertEquals(t,turmas.count());assertEquals(r,rotas.count());
    }
    @Test void impedePassageiroEOutroOrganizador(){
        String dono=usuario("MOTORISTA"),outro=usuario("MOTORISTA"),passageiro=usuario("PASSAGEIRO");var e=service.criar(dono,dados(12,null));
        assertThrows(AccessDeniedException.class,()->service.criar(passageiro,dados(12,null)));
        assertThrows(AccessDeniedException.class,()->service.consultar(e.id(),outro));
        assertThrows(AccessDeniedException.class,()->service.atualizar(e.id(),outro,dados(20,e.versao())));
        assertThrows(AccessDeniedException.class,()->service.cancelar(e.id(),outro));
        assertThrows(AccessDeniedException.class,()->service.publicar(e.id(),outro,e.versao()));
        assertTrue(service.minhas(outro,0,20).itens().isEmpty());assertEquals(1,service.minhas(dono,0,20).total());
    }
    @Test void validaCapacidadeCoordenadasEHorarios(){
        String uid=usuario("MOTORISTA");assertThrows(IllegalArgumentException.class,()->service.criar(uid,dados(0,null)));
        assertThrows(IllegalArgumentException.class,()->service.criar(uid,new ExcursaoRequest("Evento","Destino",91.0,-35.0,null,null,null,clock.instant().plusSeconds(10),null,12,null)));
        assertThrows(IllegalArgumentException.class,()->service.criar(uid,new ExcursaoRequest("Evento","Destino",-8.0,-35.0,"Origem",null,null,clock.instant().plusSeconds(10),null,12,null)));
        assertThrows(IllegalArgumentException.class,()->service.criar(uid,new ExcursaoRequest("Evento","Destino",-8.0,-35.0,null,null,null,clock.instant().minusSeconds(1),null,12,null)));
        assertThrows(IllegalArgumentException.class,()->service.criar(uid,new ExcursaoRequest("Evento","Destino",-8.0,-35.0,null,null,null,clock.instant().plusSeconds(100),clock.instant().plusSeconds(20),12,null)));
    }
    @Test void versaoAntigaNaoSobrescreveEdicaoEEncerradaNaoReabre(){
        String uid=usuario("MOTORISTA");var e=service.criar(uid,dados(12,null));service.atualizar(e.id(),uid,dados(20,e.versao()));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.atualizar(e.id(),uid,dados(14,e.versao()))).getStatusCode().value());
        assertEquals(20,service.consultar(e.id(),uid).capacidade());service.cancelar(e.id(),uid);
        assertThrows(ResponseStatusException.class,()->service.publicar(e.id(),uid,service.consultar(e.id(),uid).versao()));
    }
    private ParadaExcursaoRequest parada(Long id,String nome,double latitude){return new ParadaExcursaoRequest(id,nome,latitude,-35.0,LocalTime.of(8,0),"Portão principal",true);}
    @Test void tresModosPersistemSemAlterarDestinoOuPublicarNoCatalogo(){
        String uid=usuario("MOTORISTA");var e=service.criar(uid,dados(12,null));long total=rotas.count();
        assertEquals(ModoColetaExcursao.PORTA_A_PORTA,e.modoColeta());assertTrue(e.paradas().isEmpty());
        e=service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(null,"Praça",-8.0)),e.versao()));
        assertEquals(1,e.paradas().size());Long id=e.id(),ponto=e.paradas().get(0).id();
        e=service.configurarColeta(id,uid,new ColetaExcursaoRequest(ModoColetaExcursao.MULTIPLAS_PARADAS,java.util.List.of(parada(ponto,"Praça",-8.0),parada(null,"Terminal",-8.2)),e.versao()));
        var reaberta=service.consultar(id,uid);assertEquals(ModoColetaExcursao.MULTIPLAS_PARADAS,reaberta.modoColeta());
        assertEquals(java.util.List.of("Praça","Terminal"),reaberta.paradas().stream().map(ParadaExcursaoDTO::nome).toList());
        assertEquals("Centro de eventos",reaberta.destinoNome());assertEquals(-8.1,reaberta.destinoLatitude());assertEquals(total,rotas.count());
        e=service.configurarColeta(id,uid,new ColetaExcursaoRequest(ModoColetaExcursao.PORTA_A_PORTA,java.util.List.of(),e.versao()));
        assertTrue(e.paradas().isEmpty());assertEquals(2,paradas.findByExcursaoIdOrderByOrdemAscIdAsc(id).size());
    }
    @Test void validaQuantidadePinCoordenadasEPropriedadeDasParadas(){
        String uid=usuario("MOTORISTA");var e=service.criar(uid,dados(12,null));
        assertThrows(IllegalArgumentException.class,()->service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(),e.versao())));
        assertThrows(IllegalArgumentException.class,()->service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.MULTIPLAS_PARADAS,java.util.List.of(parada(null,"A",-8.0)),e.versao())));
        assertThrows(IllegalArgumentException.class,()->service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(null,"A",91)),e.versao())));
        var semPin=new ParadaExcursaoRequest(null,"Rua",-8.0,-35.0,null,null,false);
        assertThrows(IllegalArgumentException.class,()->service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(semPin),e.versao())));
        var outra=service.criar(uid,dados(12,null));var configurada=service.configurarColeta(outra.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(null,"B",-8.0)),outra.versao()));
        assertThrows(IllegalArgumentException.class,()->service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(configurada.paradas().get(0).id(),"B",-8.0)),e.versao())));
        assertTrue(service.consultar(e.id(),uid).paradas().isEmpty());
    }
    @Test void coletaIsoladaPorOrganizadorEEdicaoObsoletaNaoSobrescreve(){
        String uid=usuario("MOTORISTA"),outro=usuario("MOTORISTA");var e=service.criar(uid,dados(12,null));
        var d=new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(null,"Praça",-8.0)),e.versao());
        assertThrows(AccessDeniedException.class,()->service.configurarColeta(e.id(),outro,d));
        var atual=service.configurarColeta(e.id(),uid,d);assertTrue(atual.versao()>e.versao());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.configurarColeta(e.id(),uid,d)).getStatusCode().value());
        service.cancelar(e.id(),uid);assertThrows(ResponseStatusException.class,()->service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PORTA_A_PORTA,java.util.List.of(),atual.versao())));
    }
    @Test void reordenacaoMantemIdsERemoveSomenteLogicamente(){
        String uid=usuario("MOTORISTA");var e=service.criar(uid,dados(12,null));
        e=service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.MULTIPLAS_PARADAS,java.util.List.of(parada(null,"A",-8.0),parada(null,"B",-8.2)),e.versao()));
        Long a=e.paradas().get(0).id(),b=e.paradas().get(1).id();
        e=service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.MULTIPLAS_PARADAS,java.util.List.of(parada(b,"B",-8.2),parada(a,"A",-8.0)),e.versao()));
        assertEquals(java.util.List.of(b,a),e.paradas().stream().map(ParadaExcursaoDTO::id).toList());
        e=service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(b,"B",-8.2)),e.versao()));
        assertFalse(paradas.findById(a).orElseThrow().isAtiva());assertEquals(b,e.paradas().get(0).id());
    }
    @Test void edicoesSimultaneasDeColetaNaoMisturamListas() throws Exception {
        String uid=usuario("MOTORISTA");var e=service.criar(uid,dados(12,null));
        var inicio=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            java.util.concurrent.Callable<Integer> tarefa=()->{inicio.await();try{
                service.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,java.util.List.of(parada(null,UUID.randomUUID().toString(),-8.0)),e.versao()));return 200;
            }catch(ResponseStatusException ex){return ex.getStatusCode().value();}};
            var a=pool.submit(tarefa);var b=pool.submit(tarefa);inicio.countDown();
            var resultados=java.util.List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(resultados.contains(200));assertTrue(resultados.contains(409));assertEquals(1,service.consultar(e.id(),uid).paradas().size());
        }finally{inicio.countDown();pool.shutdownNow();}
    }
}
