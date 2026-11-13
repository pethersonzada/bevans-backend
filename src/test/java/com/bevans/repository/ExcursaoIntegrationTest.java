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
}
