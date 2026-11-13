package com.bevans.repository;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({PosicaoViagemService.class,ViagemService.class,PosicaoViagemIntegrationTest.Tempo.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PosicaoViagemIntegrationTest {
    static class Relogio extends Clock {
        volatile Instant agora=Instant.now().plusSeconds(1);
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId z){return this;}
        public Instant instant(){return agora;}
    }
    @TestConfiguration static class Tempo {@Bean Clock clock(){return new Relogio();}}
    @Autowired PosicaoViagemService service;
    @Autowired ViagemService viagens;
    @Autowired ViagemRepository viagemRepository;
    @Autowired UsuarioRepository usuarios;
    @Autowired TurmaRepository turmas;
    @Autowired AlunoTurmaRepository vinculos;
    @Autowired PosicaoViagemRepository posicoes;
    @Autowired Clock clock;
    private Relogio relogio(){return (Relogio)clock;}
    private record Cenario(Long viagem,Long turma,String motorista,String passageiro,Long vinculo){}
    private Usuario usuario(String tipo){Usuario u=new Usuario();String id=UUID.randomUUID().toString();u.setNome(id);u.setEmail(id+"@test.invalid");u.setTelefone(id);u.setFirebaseUid(id);u.setTipo(tipo);return usuarios.save(u);}
    private Cenario cenario(){
        relogio().agora=Instant.now().plusSeconds(1);
        var m=usuario("MOTORISTA");var p=usuario("PASSAGEIRO");Turma t=new Turma();t.setNome("Privada");t.setMotorista(m);t.setCapacidade(12);t=turmas.save(t);
        AlunoTurma v=new AlunoTurma();v.setTurma(t);v.setAluno(p);v.setStatus("APROVADO");v=vinculos.save(v);
        viagens.iniciarRota(t.getId(),"IDA");Long id=viagemRepository.findFirstByTurmaIdAndStatus(t.getId(),"EM_ANDAMENTO").orElseThrow().getId();
        return new Cenario(id,t.getId(),m.getFirebaseUid(),p.getFirebaseUid(),v.getId());
    }
    private PosicaoRequest amostra(String id){return new PosicaoRequest(id,-8.1,-35.1,clock.instant(),8.0,10.0);}
    @Test void persisteUltimaPosicaoEReconsultaAposNovaTransacao(){
        var c=cenario();assertEquals("SEM_LOCALIZACAO",service.consultar(c.viagem(),c.passageiro()).estado());
        service.enviar(c.viagem(),c.motorista(),amostra("a"));
        assertEquals(-8.1,service.consultar(c.viagem(),c.passageiro()).latitude());
        relogio().agora=relogio().agora.plusSeconds(5);service.enviar(c.viagem(),c.motorista(),amostra("b"));
        assertEquals("b",posicoes.findById(c.viagem()).orElseThrow().getAmostraId());
    }
    @Test void repeticaoNaoRenovaTtlECoordenadasAntigasNaoSaoDivulgadas(){
        var c=cenario();var a=amostra("a");service.enviar(c.viagem(),c.motorista(),a);
        Instant recebido=posicoes.findById(c.viagem()).orElseThrow().getRecebidoEm();
        relogio().agora=relogio().agora.plusSeconds(30);
        assertEquals("DESATUALIZADA",service.enviar(c.viagem(),c.motorista(),a).estado());
        var dto=service.consultar(c.viagem(),c.passageiro());assertNull(dto.latitude());assertNull(dto.longitude());assertEquals(recebido,dto.recebidoEm());
    }
    @Test void restringeDonoPassageiroETitularidadeDaViagem(){
        var c=cenario();var outro=cenario();
        assertThrows(AccessDeniedException.class,()->service.enviar(c.viagem(),c.passageiro(),amostra("a")));
        assertThrows(AccessDeniedException.class,()->service.enviar(c.viagem(),outro.motorista(),amostra("a")));
        assertThrows(AccessDeniedException.class,()->service.consultar(c.viagem(),outro.passageiro()));
        var v=vinculos.findById(c.vinculo()).orElseThrow();v.setStatus("REJEITADO");vinculos.save(v);
        assertThrows(AccessDeniedException.class,()->service.consultar(c.viagem(),c.passageiro()));
    }
    @Test void validaCoordenadasTempoPrecisaoEOrdem(){
        var c=cenario();Instant agora=clock.instant();
        assertThrows(IllegalArgumentException.class,()->service.enviar(c.viagem(),c.motorista(),new PosicaoRequest("x",Double.NaN,-35.0,agora,4.0,null)));
        assertThrows(IllegalArgumentException.class,()->service.enviar(c.viagem(),c.motorista(),new PosicaoRequest("x",-8.0,-35.0,agora.minusSeconds(31),4.0,null)));
        assertThrows(IllegalArgumentException.class,()->service.enviar(c.viagem(),c.motorista(),new PosicaoRequest("x",-8.0,-35.0,agora.plusSeconds(6),4.0,null)));
        assertThrows(IllegalArgumentException.class,()->service.enviar(c.viagem(),c.motorista(),new PosicaoRequest("x",-8.0,-35.0,agora,-1.0,null)));
        service.enviar(c.viagem(),c.motorista(),amostra("a"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.enviar(c.viagem(),c.motorista(),amostra("b"))).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.enviar(c.viagem(),c.motorista(),new PosicaoRequest("a",-9.0,-35.0,agora,8.0,10.0))).getStatusCode().value());
        relogio().agora=relogio().agora.plusSeconds(1);
        assertEquals(429,assertThrows(ResponseStatusException.class,()->service.enviar(c.viagem(),c.motorista(),amostra("c"))).getStatusCode().value());
    }
    @Test void finalizarLimpaPosicaoERejeitaAmostraDaViagemEncerrada(){
        var c=cenario();service.enviar(c.viagem(),c.motorista(),amostra("a"));viagens.encerrarRota(c.turma());
        var d=service.consultar(c.viagem(),c.passageiro());assertEquals("FINALIZADA",d.estado());assertNull(d.latitude());
        assertNull(posicoes.findById(c.viagem()).orElseThrow().getLatitude());
        viagens.iniciarRota(c.turma(),"VOLTA");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.enviar(c.viagem(),c.motorista(),amostra("b"))).getStatusCode().value());
    }
    @Test void enviosSimultaneosNaoBurlamLimiteDeFrequencia() throws Exception {
        var c=cenario();var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            Callable<Integer> tarefa=()->{inicio.await();try{service.enviar(c.viagem(),c.motorista(),amostra(UUID.randomUUID().toString()));return 200;}catch(ResponseStatusException e){return e.getStatusCode().value();}};
            var a=pool.submit(tarefa);var b=pool.submit(tarefa);inicio.countDown();var codigos=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,codigos.stream().filter(i->i==200).count());assertTrue(codigos.contains(409)||codigos.contains(429));
        }finally{pool.shutdownNow();}
    }
    @Test void finalizarConcorrenteComEnvioNuncaDeixaGpsNaViagemEncerrada() throws Exception {
        var c=cenario();var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{inicio.await();try{service.enviar(c.viagem(),c.motorista(),amostra("a"));}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());}return true;});
            var b=pool.submit(()->{inicio.await();viagens.encerrarRota(c.turma());return true;});inicio.countDown();a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
            assertEquals("FINALIZADA",service.consultar(c.viagem(),c.passageiro()).estado());
            assertTrue(posicoes.findById(c.viagem()).map(p->p.getLatitude()==null).orElse(true));
        }finally{pool.shutdownNow();}
    }
}
