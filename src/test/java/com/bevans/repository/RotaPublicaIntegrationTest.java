package com.bevans.repository;

import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.service.*;
import com.bevans.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.sql.init.mode=never"})
@Import({RotaPublicaService.class,TurmaService.class})
class RotaPublicaIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired RotaPublicaService service;
    @Autowired TurmaService turmaService;
    @Autowired ParadaPublicaRepository paradas;
    @MockBean GeocodingService geocoding;
    private Usuario usuario(String tipo) {
        String chave=UUID.randomUUID().toString();Usuario u=new Usuario();u.setNome(chave);u.setEmail(chave+"@example.test");u.setTelefone(chave);u.setTipo(tipo);return em.persist(u);
    }
    private Turma turma() {
        Turma t=new Turma();t.setNome("Privada");t.setCapacidade(12);t.setMotorista(usuario("MOTORISTA"));t.setOrigemNome("Garagem privada");t.setOrigemLatitude(-9.0);t.setOrigemLongitude(-36.0);return em.persist(t);
    }
    private RotaPublicaRequest dados(Turma t,ModoRota modo) {
        return new RotaPublicaRequest(t.getId(),"Campus Centro","Caruaru",CategoriaRota.UNIVERSITARIA,modo,"Terminal público",-8.2,-35.9,"Campus",-8.3,-36.0,"Rota urbana",null,"Centro",Set.of(DayOfWeek.MONDAY),LocalTime.of(7,0),LocalTime.of(18,0));
    }
    private RotaPublicaDTO criar(Turma t,ModoRota modo) {return service.criar(t.getMotorista().getId(),dados(t,modo));}
    @Test void configuracaoNaoPublicaTurmaENaoCopiaGaragemPrivada() {
        Turma t=turma();var r=criar(t,ModoRota.FIXA);assertFalse(r.publicada());assertEquals("Terminal público",r.origemNome());
        assertEquals(-8.2,r.origemLatitude());assertEquals("Garagem privada",t.getOrigemNome());
        assertTrue(service.publicar(r.id(),t.getMotorista().getId(),true).publicada());
        assertFalse(service.disponibilidade(r.id(),t.getMotorista().getId(),false).publicada());
    }
    @Test void paradasReordenamRenomeiamPersistemEExcluemLogicamente() {
        Turma t=turma();Long dono=t.getMotorista().getId();var r=criar(t,ModoRota.FIXA);
        var lista=service.salvarParadas(r.id(),dono,List.of(new ParadaPublicaDTO(null,"A",-8.21,-35.91,null),new ParadaPublicaDTO(null,"B",-8.22,-35.92,null))).paradas();
        var b=lista.get(1);var a=lista.get(0);
        service.salvarParadas(r.id(),dono,List.of(new ParadaPublicaDTO(b.id(),"B novo",-8.23,-35.93,null),a));
        em.flush();em.clear();var reaberta=service.consultar(r.id());
        assertEquals(b.id(),reaberta.paradas().get(0).id());assertEquals("B novo",reaberta.paradas().get(0).nome());
        assertEquals(-8.23,reaberta.paradas().get(0).latitude());
        service.salvarParadas(r.id(),dono,List.of(a));assertEquals(2,paradas.findByRotaIdOrderByOrdemAscIdAsc(r.id()).size());
        assertEquals(1,service.consultar(r.id()).paradas().size());
    }
    @Test void motoristaAlheioNaoEditaNemPublica() {
        Turma t=turma();var r=criar(t,ModoRota.FIXA);Long outro=usuario("MOTORISTA").getId();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.publicar(r.id(),outro,true));
    }
    @Test void paradaAlheiaDuplicadaEInvalidaSaoRejeitadas() {
        Turma t=turma();Long dono=t.getMotorista().getId();var r=criar(t,ModoRota.FIXA);
        assertThrows(IllegalArgumentException.class,()->service.salvarParadas(r.id(),dono,List.of(new ParadaPublicaDTO(999L,"Alheia",-8.0,-35.0,null))));
        assertThrows(IllegalArgumentException.class,()->service.salvarParadas(r.id(),dono,List.of(new ParadaPublicaDTO(null,"Inválida",Double.NaN,-35.0,null))));
    }
    @Test void rotaFixaPreservaOrdemEInverteNaVolta() {
        Turma t=turma();Long dono=t.getMotorista().getId();var r=criar(t,ModoRota.FIXA);
        service.salvarParadas(r.id(),dono,List.of(new ParadaPublicaDTO(null,"A",-8.29,-35.99,null),new ParadaPublicaDTO(null,"B",-8.21,-35.91,null)));
        var ida=service.planejamento(r.id(),dono,"IDA",true);var volta=service.planejamento(r.id(),dono,"VOLTA",true);
        assertEquals(List.of("Terminal público","A","B","Campus"),ida.stream().map(PontoPublicoDTO::nome).toList());
        assertEquals(List.of("Campus","B","A","Terminal público"),volta.stream().map(PontoPublicoDTO::nome).toList());
    }
    @Test void rotaFlexivelOtimizaSemTrocarExtremosNemPersistirOrdem() {
        Turma t=turma();Long dono=t.getMotorista().getId();var r=criar(t,ModoRota.FLEXIVEL);
        service.salvarParadas(r.id(),dono,List.of(new ParadaPublicaDTO(null,"Longe",-8.29,-35.99,null),new ParadaPublicaDTO(null,"Perto",-8.21,-35.91,null)));
        var p=service.planejamento(r.id(),dono,"IDA",true);assertEquals("Terminal público",p.get(0).nome());assertEquals("Campus",p.get(p.size()-1).nome());
        assertEquals(4,p.size());assertEquals("Longe",service.consultar(r.id()).paradas().get(0).nome());
    }
    @Test void viagemAtivaBloqueiaMudancaDeParadasEPublicacao() {
        Turma t=turma();Long dono=t.getMotorista().getId();var r=criar(t,ModoRota.FIXA);
        Viagem v=new Viagem();v.setTurma(t);v.setSentido("IDA");v.setStatus("EM_ANDAMENTO");em.persistAndFlush(v);
        assertThrows(SolicitacaoConflictException.class,()->service.salvarParadas(r.id(),dono,List.of()));
        assertThrows(SolicitacaoConflictException.class,()->service.publicar(r.id(),dono,true));
    }
    @Test void solicitaSomentePublicadaEReutilizaVinculoCancelado() {
        Turma t=turma();var r=criar(t,ModoRota.FIXA);Long aluno=usuario("PASSAGEIRO").getId();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.solicitar(r.id(),aluno));
        service.publicar(r.id(),t.getMotorista().getId(),true);
        var pedido=service.solicitar(r.id(),aluno);assertEquals("PENDENTE",pedido.status());
        assertThrows(SolicitacaoConflictException.class,()->service.solicitar(r.id(),aluno));
        turmaService.cancelarSolicitacao(pedido.id(),aluno);var novo=service.solicitar(r.id(),aluno);assertEquals(pedido.id(),novo.id());
    }
}
