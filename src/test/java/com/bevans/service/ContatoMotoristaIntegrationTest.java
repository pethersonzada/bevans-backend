package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:contato;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
class ContatoMotoristaIntegrationTest {
    @Autowired ContatoMotoristaService service;@Autowired UsuarioRepository usuarios;@Autowired TurmaRepository turmas;@Autowired AlunoTurmaRepository matriculas;
    @Autowired ExcursaoService excursoes;@Autowired ConviteExcursaoService convites;@Autowired InscricaoExcursaoService inscricoes;@Autowired Clock clock;
    private Usuario usuario(String tipo){var u=new Usuario();String id=UUID.randomUUID().toString();u.setNome(id);u.setTelefone(id);u.setEmail(id+"@test.invalid");u.setFirebaseUid(id);u.setTipo(tipo);return usuarios.save(u);}
    private ContatoMotoristaDTO.Configurar ligar(long versao){return new ContatoMotoristaDTO.Configurar("+55 (81) 99999-8888",true,true,versao);}
    @Test void optInExigeTelefoneValidoEPassageiroNaoConfiguraContato(){
        var m=usuario("MOTORISTA");assertFalse(service.minha(m.getFirebaseUid()).habilitado());
        assertThrows(AccessDeniedException.class,()->service.configurar(usuario("PASSAGEIRO").getFirebaseUid(),ligar(0)));
        assertThrows(IllegalArgumentException.class,()->service.configurar(m.getFirebaseUid(),new ContatoMotoristaDTO.Configurar("5581999998888",true,false,0L)));
        for(String invalido:List.of("81999998888","+1 5551234567","+55 20 999998888","https://wa.me/5581999998888","5581999998888\r\nX:1"))assertThrows(IllegalArgumentException.class,()->ContatoMotoristaService.normalizar(invalido));
        var c=service.configurar(m.getFirebaseUid(),ligar(0));assertEquals("5581999998888",c.telefone());assertEquals(1,c.versao());assertNotNull(c.consentimentoEm());
        assertThrows(ResponseStatusException.class,()->service.configurar(m.getFirebaseUid(),ligar(0)));
    }
    @Test void somenteAprovadosConsultamEDesabilitacaoOuRemocaoRevogaAcesso(){
        var m=usuario("MOTORISTA");var p=usuario("PASSAGEIRO");var outro=usuario("PASSAGEIRO");var t=new Turma();t.setNome("Privada");t.setMotorista(m);t.setCapacidade(5);t=turmas.save(t);Long id=t.getId();
        var v=new AlunoTurma();v.setTurma(t);v.setAluno(p);v.setStatus("PENDENTE");v=matriculas.save(v);var c=service.configurar(m.getFirebaseUid(),ligar(0));
        assertThrows(ResponseStatusException.class,()->service.turma(id,p.getFirebaseUid()));v.setStatus("APROVADO");v=matriculas.save(v);
        assertEquals("https://wa.me/5581999998888",service.turma(id,p.getFirebaseUid()).waMe());assertThrows(ResponseStatusException.class,()->service.turma(id,outro.getFirebaseUid()));
        c=service.configurar(m.getFirebaseUid(),new ContatoMotoristaDTO.Configurar(null,false,false,c.versao()));assertThrows(ResponseStatusException.class,()->service.turma(id,p.getFirebaseUid()));
        service.configurar(m.getFirebaseUid(),ligar(c.versao()));v.setStatus("REMOVIDO");matriculas.save(v);assertThrows(ResponseStatusException.class,()->service.turma(id,p.getFirebaseUid()));
    }
    @Test void excursaoExigeCredencialIndividualConfirmadaENaoConvitePublico(){
        var m=usuario("MOTORISTA");service.configurar(m.getFirebaseUid(),ligar(0));var e=excursoes.criar(m.getFirebaseUid(),new ExcursaoRequest("Contato","Destino",-8.3,-35.3,"Origem",-8.0,-35.0,clock.instant().plusSeconds(86400),null,5,null));e=excursoes.publicar(e.id(),m.getFirebaseUid(),e.versao());
        var convite=convites.emitir(e.id(),m.getFirebaseUid(),clock.instant().plusSeconds(3600));String token=inscricoes.emitirCredencial(convite.token()).token();var i=inscricoes.solicitar(convite.token(),token,new InscricaoExcursaoRequest("Pessoa","fixture@test.invalid",null,"Pin",-8.1,-35.1,true,true));
        assertThrows(ResponseStatusException.class,()->service.inscricao(convite.token()));assertThrows(ResponseStatusException.class,()->service.inscricao(token));
        inscricoes.confirmar(e.id(),i.id(),m.getFirebaseUid());assertEquals(m.getId(),service.inscricao(token).motoristaId());inscricoes.cancelar(token);assertThrows(ResponseStatusException.class,()->service.inscricao(token));
    }
    @Test void configuracoesConcorrentesNaoPerdemRevogacaoPorVersaoObsoleta()throws Exception{
        var m=usuario("MOTORISTA");var c=service.configurar(m.getFirebaseUid(),ligar(0));var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var tarefas=java.util.stream.IntStream.range(0,2).mapToObj(n->pool.submit(()->{inicio.await();try{service.configurar(m.getFirebaseUid(),new ContatoMotoristaDTO.Configurar(n==0?null:"5581999997777",n!=0,n!=0,c.versao()));return 200;}catch(ResponseStatusException ex){return ex.getStatusCode().value();}})).toList();inicio.countDown();var resultados=List.of(tarefas.get(0).get(20,TimeUnit.SECONDS),tarefas.get(1).get(20,TimeUnit.SECONDS));assertTrue(resultados.contains(200));assertTrue(resultados.contains(409));}
        finally{inicio.countDown();pool.shutdownNow();}
    }
}
