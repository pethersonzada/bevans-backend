package com.bevans.service;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:responsaveis;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@Import(ResponsavelIntegrationTest.VerificadorTeste.class)
class ResponsavelIntegrationTest {
    static class ProvaTeste implements VerificacaoResponsabilidade {
        volatile boolean comprovada;
        public Resultado verificar(String r,String p){return new Resultado(comprovada,"evidencia-apenas-teste");}
    }
    @TestConfiguration static class VerificadorTeste {@Bean @Primary ProvaTeste provaTeste(){return new ProvaTeste();}}
    @Autowired ResponsavelService service;@Autowired ProvaTeste prova;
    @Autowired UsuarioRepository usuarios;@Autowired ConviteResponsavelRepository convites;
    @Autowired VinculoResponsavelRepository vinculos;@Autowired TurmaRepository turmas;@Autowired AlunoTurmaRepository matriculas;
    @Autowired EnderecoRepository enderecos;@Autowired PresencaRepository presencas;@Autowired ViagemService viagens;@Autowired ViagemRepository viagensRepo;
    @Autowired PosicaoViagemService gps;@Autowired Clock clock;
    @BeforeEach void iniciar(){prova.comprovada=true;}
    private Usuario usuario(String tipo){var u=new Usuario();String id=UUID.randomUUID().toString();u.setNome(id);u.setTelefone(id);u.setEmail(id+"@test.invalid");u.setFirebaseUid(id);u.setTipo(tipo);return usuarios.save(u);}
    private ResponsavelDTO solicitar(Usuario p,Usuario r){var convite=service.emitirConvite(p.getFirebaseUid());return service.solicitar(r.getFirebaseUid(),new ResponsavelDTO.Solicitar(convite.token(),true,"1"));}
    private Long viagem(Usuario p){
        var m=usuario("MOTORISTA");var t=new Turma();t.setNome("Privada");t.setCapacidade(5);t.setMotorista(m);t.setOrigemNome("Origem");t.setOrigemLatitude(-8.0);t.setOrigemLongitude(-35.0);t.setDestinoNome("Destino");t.setDestinoLatitude(-8.3);t.setDestinoLongitude(-35.3);t=turmas.save(t);
        var v=new AlunoTurma();v.setTurma(t);v.setAluno(p);v.setStatus("APROVADO");matriculas.save(v);
        var e=new Endereco();e.setUsuario(p);e.setLatitude(-8.1);e.setLongitude(-35.1);e=enderecos.save(e);
        var pr=new Presenca();pr.setUsuario(p);pr.setTurma(t);pr.setData(LocalDate.now(clock));pr.setStatus("IDA");pr.setEnderecoIda(e);presencas.save(pr);
        viagens.iniciarRota(t.getId(),"IDA");Long id=viagensRepo.findFirstByTurmaIdAndStatus(t.getId(),"EM_ANDAMENTO").orElseThrow().getId();
        gps.enviar(id,m.getFirebaseUid(),new PosicaoRequest("gps",-8.1,-35.1,clock.instant(),5.0,null));return id;
    }
    @Test void vinculoComprovadoConsultaSomenteOperacaoDoProprioPassageiroERevogacaoEImediata(){
        var p=usuario("PASSAGEIRO");var r=usuario("RESPONSAVEL");var outro=usuario("RESPONSAVEL");var v=solicitar(p,r);Long viagem=viagem(p);
        Long pendente=v.id();assertThrows(AccessDeniedException.class,()->service.localizacao(pendente,viagem,r.getFirebaseUid()));
        v=service.aprovar(v.id(),p.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"1"));assertEquals(EstadoResponsabilidade.ATIVO,v.estado());Long id=v.id();
        assertEquals(p.getId(),service.operacao(id,viagem,r.getFirebaseUid()).alunoId());assertEquals(-8.1,service.localizacao(id,viagem,r.getFirebaseUid()).latitude());
        assertThrows(ResponseStatusException.class,()->service.localizacao(id,viagem,outro.getFirebaseUid()));
        prova.comprovada=false;assertThrows(AccessDeniedException.class,()->service.localizacao(id,viagem,r.getFirebaseUid()));prova.comprovada=true;
        v=service.revogar(id,r.getFirebaseUid(),new ResponsavelDTO.Revogar(v.versao()));assertEquals(EstadoResponsabilidade.REVOGADO,v.estado());
        assertThrows(AccessDeniedException.class,()->service.operacao(id,viagem,r.getFirebaseUid()));assertEquals(3,service.historico(id,p.getFirebaseUid(),0,20).getTotalElements());
    }
    @Test void consentimentoSemComprovacaoNaoLiberaDadosEAdaptadorPadraoNega(){
        assertFalse(new VerificacaoResponsabilidadePendente().verificar("r","p").comprovada());prova.comprovada=false;
        var p=usuario("PASSAGEIRO");var r=usuario("RESPONSAVEL");var v=solicitar(p,r);v=service.aprovar(v.id(),p.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"1"));
        assertEquals(EstadoResponsabilidade.AGUARDANDO_VERIFICACAO,v.estado());assertNotNull(v.consentimentoPassageiroEm());Long id=v.id();
        assertThrows(AccessDeniedException.class,()->service.operacao(id,1L,r.getFirebaseUid()));
    }
    @Test void conviteEDeUsoUnicoRevogadoOuExpiradoNaoFuncionaENaoAceitaIdArbitrario(){
        var p=usuario("PASSAGEIRO");var r=usuario("RESPONSAVEL");var c=service.emitirConvite(p.getFirebaseUid());
        service.revogarConvites(p.getFirebaseUid());assertThrows(ResponseStatusException.class,()->service.solicitar(r.getFirebaseUid(),new ResponsavelDTO.Solicitar(c.token(),true,"1")));
        var novo=service.emitirConvite(p.getFirebaseUid());var entidade=convites.findByTokenHash(TokensExcursao.hash(novo.token())).orElseThrow();entidade.expiraEm=clock.instant().minusSeconds(1);convites.save(entidade);
        assertThrows(ResponseStatusException.class,()->service.solicitar(r.getFirebaseUid(),new ResponsavelDTO.Solicitar(novo.token(),true,"1")));
        var valido=service.emitirConvite(p.getFirebaseUid());service.solicitar(r.getFirebaseUid(),new ResponsavelDTO.Solicitar(valido.token(),true,"1"));
        assertThrows(ResponseStatusException.class,()->service.solicitar(usuario("RESPONSAVEL").getFirebaseUid(),new ResponsavelDTO.Solicitar(valido.token(),true,"1")));
        assertThrows(AccessDeniedException.class,()->service.emitirConvite(r.getFirebaseUid()));assertFalse(valido.toString().contains(valido.token()));
    }
    @Test void somentePassageiroAutorizaSemPromocaoPorPayloadESemAceiteImplicito(){
        var p=usuario("PASSAGEIRO");var r=usuario("RESPONSAVEL");var v=solicitar(p,r);
        assertThrows(AccessDeniedException.class,()->service.aprovar(v.id(),r.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"1")));
        assertThrows(ResponseStatusException.class,()->service.aprovar(v.id(),usuario("PASSAGEIRO").getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"1")));
        assertThrows(IllegalArgumentException.class,()->service.aprovar(v.id(),p.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),false,"1")));
        assertThrows(IllegalArgumentException.class,()->service.aprovar(v.id(),p.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"antigo")));
        assertTrue(service.listar(usuario("RESPONSAVEL").getFirebaseUid(),0,20).isEmpty());
    }
    @Test void aprovarERevogarConcorrentementeNaoReativaVinculoRevogado()throws Exception{
        var p=usuario("PASSAGEIRO");var r=usuario("RESPONSAVEL");var v=solicitar(p,r);var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{inicio.await();try{return service.aprovar(v.id(),p.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"1"));}catch(ResponseStatusException ex){assertEquals(409,ex.getStatusCode().value());return null;}});
            var b=pool.submit(()->{inicio.await();try{return service.revogar(v.id(),r.getFirebaseUid(),new ResponsavelDTO.Revogar(v.versao()));}catch(ResponseStatusException ex){assertEquals(409,ex.getStatusCode().value());return null;}});inicio.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
            var atual=vinculos.findById(v.id()).orElseThrow();if(atual.estado!=EstadoResponsabilidade.REVOGADO)service.revogar(v.id(),r.getFirebaseUid(),new ResponsavelDTO.Revogar(atual.versao));
            assertEquals(EstadoResponsabilidade.REVOGADO,vinculos.findById(v.id()).orElseThrow().estado);
            assertThrows(ResponseStatusException.class,()->service.aprovar(v.id(),p.getFirebaseUid(),new ResponsavelDTO.Aprovar(v.versao(),true,"1")));
        }finally{inicio.countDown();pool.shutdownNow();}
    }
}
