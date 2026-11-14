package com.bevans.service;
import com.bevans.model.Usuario;
import com.bevans.repository.UsuarioRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:identidade;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
class AutenticacaoPerfilIntegrationTest {
    @Autowired AutenticacaoPerfilService service;@Autowired UsuarioRepository usuarios;
    private Usuario usuario(String uid){var u=new Usuario();String id=UUID.randomUUID().toString();u.setNome(id);u.setTelefone(id);u.setEmail(id+"@test.invalid");u.setFirebaseUid(uid);u.setTipo("MOTORISTA");return usuarios.saveAndFlush(u);}
    @Test void legadoExigeEmailVerificadoMasPerfilJaVinculadoPreservaLogin(){
        var u=usuario(null);String uid=UUID.randomUUID().toString();
        assertThrows(AccessDeniedException.class,()->service.login(uid,u.getEmail(),u.getEmail(),false));assertNull(usuarios.findById(u.getId()).orElseThrow().getFirebaseUid());
        assertEquals(u.getId().toString(),service.login(uid,u.getEmail(),u.getEmail(),true).get("id"));
        assertEquals(u.getId().toString(),service.login(uid,"email-atualizado@test.invalid","email-atualizado@test.invalid",false).get("id"));
        assertThrows(AccessDeniedException.class,()->service.login("outro-uid",u.getEmail(),u.getEmail(),true));
    }
    @Test void bancoRecusaDuplicidadeDeUidMesmoForaDaValidacaoDoController(){
        String uid=UUID.randomUUID().toString();usuario(uid);assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->usuario(uid));
    }
    @Test void doisUidsDisputandoPerfilLegadoNaoSeSobrescrevem()throws Exception{
        var u=usuario(null);String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();var inicio=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var resultados=List.of(a,b).stream().map(uid->pool.submit(()->{inicio.await();try{service.login(uid,u.getEmail(),u.getEmail(),true);return 200;}catch(AccessDeniedException ex){return 403;}})).toList();inicio.countDown();var estados=List.of(resultados.get(0).get(20,TimeUnit.SECONDS),resultados.get(1).get(20,TimeUnit.SECONDS));assertTrue(estados.contains(200));assertTrue(estados.contains(403));assertTrue(List.of(a,b).contains(usuarios.findById(u.getId()).orElseThrow().getFirebaseUid()));}
        finally{inicio.countDown();pool.shutdownNow();}
    }
}
