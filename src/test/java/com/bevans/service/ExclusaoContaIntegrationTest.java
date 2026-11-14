package com.bevans.service;
import com.bevans.model.*;
import com.bevans.repository.*;
import com.google.firebase.auth.FirebaseAuth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:exclusao;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
class ExclusaoContaIntegrationTest {
    @Autowired UsuarioService service;@Autowired UsuarioRepository usuarios;@Autowired AlunoTurmaRepository matriculas;@Autowired TurmaRepository turmas;
    @Autowired ExclusaoContaService exclusao;@Autowired ExclusaoContaRepository exclusoes;@Autowired AutenticacaoPerfilService perfis;
    @Autowired org.springframework.transaction.PlatformTransactionManager tx;@Autowired java.time.Clock clock;
    private Usuario usuario(String tipo){var u=new Usuario();String id=UUID.randomUUID().toString();u.setNome(id);u.setTelefone(id);u.setEmail(id+"@test.invalid");u.setFirebaseUid(id);u.setTipo(tipo);return usuarios.save(u);}
    @Test void conflitoDeRetencaoNaoPodeExcluirIdentidadeFirebaseAntesDoBanco()throws Exception{
        var p=usuario("PASSAGEIRO");var m=usuario("MOTORISTA");var t=new Turma();t.setNome("Histórico");t.setMotorista(m);t=turmas.save(t);var v=new AlunoTurma();v.setAluno(p);v.setTurma(t);v.setStatus("APROVADO");matriculas.save(v);
        var auth=mock(FirebaseAuth.class);try(var scope=mockStatic(FirebaseAuth.class)){scope.when(FirebaseAuth::getInstance).thenReturn(auth);assertThrows(com.bevans.exception.ConflitoException.class,()->service.excluirUsuario(p.getId()));verify(auth,never()).deleteUser(anyString());}
        assertTrue(usuarios.findById(p.getId()).isPresent());assertFalse(exclusoes.existsByFirebaseUid(p.getFirebaseUid()));
    }
    @Test void exclusaoLocalEFilaSaoAtomicasENaoPermitemRecadastroOuLoginDoMesmoUid(){
        var p=usuario("PASSAGEIRO");service.excluirUsuario(p.getId());assertTrue(usuarios.findById(p.getId()).isEmpty());assertTrue(exclusoes.existsByFirebaseUid(p.getFirebaseUid()));
        var novo=new Usuario();novo.setNome("Novo");novo.setTelefone(UUID.randomUUID().toString());novo.setEmail(UUID.randomUUID()+"@test.invalid");novo.setTipo("PASSAGEIRO");novo.setFirebaseUid(p.getFirebaseUid());
        assertThrows(com.bevans.exception.ConflitoException.class,()->service.cadastrarUsuario(novo));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->perfis.login(p.getFirebaseUid(),p.getEmail(),p.getEmail(),true));
        new ExclusaoContaWorker(exclusao,new FirebaseExclusaoGateway(false)).executar();
        var e=exclusoes.findAll().stream().filter(v->v.firebaseUid.equals(p.getFirebaseUid())).findFirst().orElseThrow();assertEquals("PENDENTE",e.estado);assertEquals(0,e.tentativas);assertNull(e.concluidaEm);
    }
    @Test void rollbackNaoDeixaPerfilExcluidoOuTarefaRemota(){
        var p=usuario("PASSAGEIRO");new org.springframework.transaction.support.TransactionTemplate(tx).execute(status->{service.excluirUsuario(p.getId());status.setRollbackOnly();return null;});
        assertTrue(usuarios.findById(p.getId()).isPresent());assertFalse(exclusoes.existsByFirebaseUid(p.getFirebaseUid()));
    }
    @Test void reservaVencidaPodeSerRecuperadaERespostaAntigaNaoConcluiNovaReserva(){
        var p=usuario("PASSAGEIRO");service.excluirUsuario(p.getId());var primeira=exclusao.reservar();assertNotNull(primeira);
        var e=exclusoes.findById(primeira.id()).orElseThrow();e.proximaTentativa=clock.instant().minusSeconds(1);exclusoes.saveAndFlush(e);var segunda=exclusao.reservar();assertEquals(primeira.id(),segunda.id());assertEquals(2,segunda.tentativa());
        exclusao.concluir(primeira,ExclusaoIdentidadeGateway.Resultado.EXCLUIDA);assertEquals("PROCESSANDO",exclusoes.findById(e.id).orElseThrow().estado);
        exclusao.concluir(segunda,ExclusaoIdentidadeGateway.Resultado.EXCLUIDA);exclusao.concluir(segunda,ExclusaoIdentidadeGateway.Resultado.EXCLUIDA);assertEquals("CONCLUIDA",exclusoes.findById(e.id).orElseThrow().estado);
    }
    @Test void cincoFalhasTransitoriasInterrompemTentativasAutomaticas(){
        var p=usuario("PASSAGEIRO");service.excluirUsuario(p.getId());Long id=null;
        for(int n=1;n<=5;n++){
            if(id!=null){var e=exclusoes.findById(id).orElseThrow();e.proximaTentativa=clock.instant().minusSeconds(1);exclusoes.saveAndFlush(e);}
            var pedido=exclusao.reservar();assertNotNull(pedido);id=pedido.id();exclusao.concluir(pedido,ExclusaoIdentidadeGateway.Resultado.TRANSITORIO);assertEquals(n,exclusoes.findById(id).orElseThrow().tentativas);
        }
        assertEquals("FALHA",exclusoes.findById(id).orElseThrow().estado);assertNull(exclusao.reservar());
    }
}
