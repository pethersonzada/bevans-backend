package com.bevans.security;
import com.bevans.model.*;
import com.bevans.dto.*;
import com.bevans.repository.*;
import com.bevans.service.ColetaPrivadaService;
import com.google.firebase.auth.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:coleta-http;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@AutoConfigureMockMvc
class ColetaPrivadaFluxoHttpTest {
    @Autowired MockMvc mvc;@Autowired UsuarioRepository usuarios;@Autowired TurmaRepository turmas;
    @Autowired AlunoTurmaRepository vinculos;@Autowired PresencaRepository presencas;@Autowired EnderecoRepository enderecos;
    @Autowired ColetaPrivadaService service;
    private Usuario usuario(String tipo){String uid=UUID.randomUUID().toString();var u=new Usuario();u.setNome(uid);u.setTelefone(uid);u.setEmail(uid+"@test.invalid");u.setTipo(tipo);u.setFirebaseUid(uid);return usuarios.save(u);}
    @Test void confirmaPresencaPorParadaSemExigirOuApagarEnderecosERejeitaTerceiros() throws Exception {
        var dono=usuario("MOTORISTA");var aluno=usuario("PASSAGEIRO");var outro=usuario("PASSAGEIRO");var t=new Turma();t.setNome("Privada");t.setMotorista(dono);t.setCapacidade(12);t=turmas.save(t);
        var v=new AlunoTurma();v.setTurma(t);v.setAluno(aluno);v.setStatus("APROVADO");vinculos.save(v);
        var a=new Endereco();a.setUsuario(aluno);a.setLatitude(-8.0);a.setLongitude(-35.0);a=enderecos.save(a);var b=new Endereco();b.setUsuario(aluno);b.setLatitude(-8.1);b.setLongitude(-35.1);b=enderecos.save(b);
        var p=new Presenca();p.setTurma(t);p.setUsuario(aluno);p.setData(LocalDate.now(ZoneId.of("America/Sao_Paulo")));p.setStatus("NAO_VOU");p.setEnderecoIda(a);p.setEnderecoVolta(b);presencas.save(p);
        for(String s:List.of("IDA","VOLTA")){var cfg=service.configurar(t.getId(),s,dono.getFirebaseUid(),new ColetaPrivadaRequest(ModoColetaPrivada.PARADAS_DEFINIDAS,0L,List.of(new ColetaPrivadaRequest.Parada(null,"Praça",-8.2,-35.2,null,true))));service.escolher(t.getId(),s,aluno.getFirebaseUid(),null,cfg.paradas().get(0).id(),cfg.versao());}
        mvc.perform(get("/turmas/"+t.getId()+"/coleta/IDA")).andExpect(status().isUnauthorized());
        FirebaseAuth auth=mock(FirebaseAuth.class);FirebaseToken decoded=mock(FirebaseToken.class);when(decoded.getUid()).thenReturn(aluno.getFirebaseUid());when(auth.verifyIdToken("valido")).thenReturn(decoded);
        try(var scope=mockStatic(FirebaseAuth.class)){
            scope.when(FirebaseAuth::getInstance).thenReturn(auth);
            mvc.perform(post("/rota/confirmar").param("turmaId",t.getId().toString()).param("usuarioId",aluno.getId().toString()).param("status","AMBOS").header("Authorization","Bearer valido")).andExpect(status().isOk());
            mvc.perform(get("/turmas/"+t.getId()+"/coleta/IDA").header("Authorization","Bearer valido")).andExpect(status().isOk()).andExpect(jsonPath("$.escolhidaId").isNumber()).andExpect(jsonPath("$.paradas[0].nome").value("Praça"));
            when(decoded.getUid()).thenReturn(outro.getFirebaseUid());mvc.perform(get("/turmas/"+t.getId()+"/coleta/IDA").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }
        p=presencas.findByUsuarioIdAndTurmaIdAndData(aluno.getId(),t.getId(),p.getData()).orElseThrow();assertEquals(a.getId(),p.getEnderecoIda().getId());assertEquals(b.getId(),p.getEnderecoVolta().getId());
    }
}
