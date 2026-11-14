package com.bevans.security;
import com.bevans.dto.*;
import com.bevans.model.*;
import com.bevans.repository.UsuarioRepository;
import com.bevans.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false",
 "spring.datasource.url=jdbc:h2:mem:inscricao-http;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@AutoConfigureMockMvc
class InscricaoFluxoHttpTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UsuarioRepository usuarios;
    @Autowired ExcursaoService excursoes;@Autowired ConviteExcursaoService convites;
    @Test void fluxoPublicoRealReservaConfirmaECancelaSemDarAcessoAdministrativoOuGps() throws Exception {
        String uid=UUID.randomUUID().toString();var u=new Usuario();u.setNome("Organizador");u.setTelefone(uid);u.setEmail(uid+"@test.invalid");u.setFirebaseUid(uid);u.setTipo("MOTORISTA");usuarios.save(u);
        var e=excursoes.criar(uid,new ExcursaoRequest("Evento","Destino",-8.3,-35.3,null,null,null,Instant.now().plusSeconds(86400),null,1,null));
        e=excursoes.publicar(e.id(),uid,e.versao());var convite=convites.emitir(e.id(),uid,Instant.now().plusSeconds(3600));
        mvc.perform(get("/publico/excursoes/convite").header("X-Convite",convite.token())).andExpect(status().isOk()).andExpect(jsonPath("$.vagasDisponiveis").value(1));
        var cred=mvc.perform(post("/publico/excursoes/credenciais").header("X-Convite",convite.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String token=json.readTree(cred.getResponse().getContentAsString()).get("token").asText();assertEquals(43,token.length());
        var dados=new InscricaoExcursaoRequest("Participante","contato@test.invalid",null,"Meu pin",-8.12,-35.12,true,true);
        var resposta=mvc.perform(post("/publico/excursoes/inscricoes").header("X-Convite",convite.token()).header("X-Inscricao",token).contentType("application/json").content(json.writeValueAsString(dados)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("SOLICITADA")).andReturn();
        Long id=json.readTree(resposta.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/publico/excursoes/inscricao").param("inscricaoId",id.toString())).andExpect(status().isNotFound());
        mvc.perform(get("/excursoes/"+e.id()+"/inscricoes").header("X-Inscricao",token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/viagens/1/localizacao").header("X-Inscricao",token)).andExpect(status().isUnauthorized());
        FirebaseAuth auth=mock(FirebaseAuth.class);FirebaseToken decoded=mock(FirebaseToken.class);when(decoded.getUid()).thenReturn(uid);when(auth.verifyIdToken("motorista-teste")).thenReturn(decoded);
        try(var scope=mockStatic(FirebaseAuth.class)){
            scope.when(FirebaseAuth::getInstance).thenReturn(auth);
            mvc.perform(put("/excursoes/"+e.id()+"/inscricoes/"+id+"/confirmacao").header("Authorization","Bearer motorista-teste"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        }
        mvc.perform(get("/publico/excursoes/inscricao").header("X-Inscricao",token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(delete("/publico/excursoes/inscricao").header("X-Inscricao",token)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        mvc.perform(get("/publico/excursoes/convite").header("X-Convite",convite.token())).andExpect(status().isOk()).andExpect(jsonPath("$.vagasDisponiveis").value(1)).andExpect(jsonPath("$.inscritos").doesNotExist());
    }
}
