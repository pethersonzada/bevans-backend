package com.bevans.repository;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({ExcursaoService.class,ConviteExcursaoService.class,ConviteExcursaoIntegrationTest.Tempo.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ConviteExcursaoIntegrationTest {
    static class Relogio extends Clock {
        Instant agora=Instant.parse("2030-01-01T12:00:00Z");
        public Instant instant(){return agora;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
    }
    @TestConfiguration static class Tempo {@Bean Clock clock(){return new Relogio();}}
    @Autowired ExcursaoService excursoes;
    @Autowired ConviteExcursaoService service;
    @Autowired ConviteExcursaoRepository convites;
    @Autowired UsuarioRepository usuarios;
    @Autowired Clock clock;
    @BeforeEach void resetTempo(){((Relogio)clock).agora=Instant.parse("2030-01-01T12:00:00Z");}
    private String usuario(String tipo){String uid=UUID.randomUUID().toString();Usuario u=new Usuario();u.setNome(uid);u.setTelefone(uid);u.setEmail(uid+"@test.invalid");u.setFirebaseUid(uid);u.setTipo(tipo);usuarios.save(u);return uid;}
    private ExcursaoDTO criar(String uid){return excursoes.criar(uid,new ExcursaoRequest("Evento","Destino",-8.0,-35.0,"Garagem privada",-8.1,-35.1,clock.instant().plusSeconds(86400),null,12,null));}
    private ExcursaoDTO publicada(String uid){var e=criar(uid);return excursoes.publicar(e.id(),uid,e.versao());}
    @Test void tokenSoRetornaNaEmissaoEPersistenciaGuardaApenasHash(){
        String uid=usuario("MOTORISTA");var e=publicada(uid);var a=service.emitir(e.id(),uid,clock.instant().plusSeconds(3600));var b=service.emitir(e.id(),uid,clock.instant().plusSeconds(3600));
        assertEquals(43,a.token().length());assertNotEquals(a.token(),b.token());
        assertEquals(TokensExcursao.hash(a.token()),convites.findById(a.convite().id()).orElseThrow().getTokenHash());
        assertFalse(a.toString().contains(a.token()));assertEquals(2,service.listar(e.id(),uid).size());
        var publico=service.consultar(a.token());assertEquals("Destino",publico.destinoNome());assertTrue(publico.paradas().isEmpty());
    }
    @Test void expiraRevogaECancelaSemExporDadosOuEnumerar(){
        String uid=usuario("MOTORISTA");var e=publicada(uid);var a=service.emitir(e.id(),uid,clock.instant().plusSeconds(60));
        ((Relogio)clock).agora=clock.instant().plusSeconds(60);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.consultar(a.token())).getStatusCode().value());
        var b=service.emitir(e.id(),uid,clock.instant().plusSeconds(60));service.revogar(e.id(),b.convite().id(),uid);service.revogar(e.id(),b.convite().id(),uid);
        assertThrows(ResponseStatusException.class,()->service.consultar(b.token()));
        var c=service.emitir(e.id(),uid,clock.instant().plusSeconds(60));excursoes.cancelar(e.id(),uid);
        assertThrows(ResponseStatusException.class,()->service.consultar(c.token()));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.consultar("invalido")).getStatusCode().value());
    }
    @Test void apenasOrganizadorAdministraENaoPermiteRascunhoOuPrazoInvalido(){
        String uid=usuario("MOTORISTA"),outro=usuario("MOTORISTA"),aluno=usuario("PASSAGEIRO");var r=criar(uid);
        assertThrows(ResponseStatusException.class,()->service.emitir(r.id(),uid,clock.instant().plusSeconds(60)));
        var e=excursoes.publicar(r.id(),uid,r.versao());
        assertThrows(AccessDeniedException.class,()->service.emitir(e.id(),outro,clock.instant().plusSeconds(60)));
        assertThrows(AccessDeniedException.class,()->service.listar(e.id(),aluno));
        assertThrows(IllegalArgumentException.class,()->service.emitir(e.id(),uid,clock.instant().minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->service.emitir(e.id(),uid,e.partidaEm().plusSeconds(1)));
        var convite=service.emitir(e.id(),uid,clock.instant().plusSeconds(60));var outra=publicada(uid);
        assertThrows(ResponseStatusException.class,()->service.revogar(outra.id(),convite.convite().id(),uid));
        assertThrows(AccessDeniedException.class,()->service.revogar(e.id(),convite.convite().id(),outro));
        assertNotNull(service.consultar(convite.token()));
    }
    @Test void consultaPublicaContemSomenteParadasDoModoDaExcursao(){
        String uid=usuario("MOTORISTA");var e=criar(uid);
        e=excursoes.configurarColeta(e.id(),uid,new ColetaExcursaoRequest(ModoColetaExcursao.PARADA_UNICA,List.of(new ParadaExcursaoRequest(null,"Praça",-8.2,-35.2,null,"Encontro",true)),e.versao()));
        e=excursoes.publicar(e.id(),uid,e.versao());var convite=service.emitir(e.id(),uid,clock.instant().plusSeconds(60));
        var p=service.consultar(convite.token());assertEquals(ModoColetaExcursao.PARADA_UNICA,p.modoColeta());assertEquals(1,p.paradas().size());assertEquals("Praça",p.paradas().get(0).nome());
    }
}
