package com.bevans.security;

import com.bevans.config.FirebaseConfig;
import com.bevans.model.Usuario;
import com.bevans.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cadeia HTTP real + services + persistência H2. Somente Firebase é simulado. */
@SpringBootTest(properties={"spring.config.import=","spring.datasource.url=jdbc:h2:mem:public-http;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class RotasPublicasFluxoHttpTest {
    @MockBean FirebaseConfig firebaseConfig;
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ObjectMapper json;
    private Usuario usuario(String uid,String tipo) {
        Usuario u=new Usuario();u.setNome(uid);u.setEmail(uid+"@example.test");u.setTelefone(uid+"-private-phone");u.setFirebaseUid(uid);u.setTipo(tipo);return usuarios.save(u);
    }
    @Test void publicaSemExporDadosPrivadosESolicitaAprovaRespeitandoCapacidade() throws Exception {
        Usuario dono=usuario("driver-http","MOTORISTA");usuario("passenger-http","PASSAGEIRO");usuario("other-http","PASSAGEIRO");
        FirebaseAuth auth=mock(FirebaseAuth.class);
        for(String uid:List.of("driver-http","passenger-http","other-http")) {
            FirebaseToken token=mock(FirebaseToken.class);when(token.getUid()).thenReturn(uid);when(auth.verifyIdToken(uid)).thenReturn(token);
        }
        try(var scope=mockStatic(FirebaseAuth.class)) {
            scope.when(FirebaseAuth::getInstance).thenReturn(auth);
            var turma=mvc.perform(post("/turmas").header("Authorization","Bearer driver-http").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("nome","Turma privada","turno","NOITE","motorista",Map.of("id",dono.getId()),"capacidade",1))))
                    .andExpect(status().isOk()).andReturn();
            long turmaId=json.readTree(turma.getResponse().getContentAsString()).get("id").asLong();
            Map<String,Object> dados=new LinkedHashMap<>();
            dados.put("turmaId",turmaId);dados.put("nome","Campus público");dados.put("cidade","Caruaru");dados.put("modo","FIXA");dados.put("categoria","UNIVERSITARIA");
            dados.put("origemNome","Terminal");dados.put("origemLatitude",-8.2);dados.put("origemLongitude",-35.9);
            dados.put("destinoNome","Campus");dados.put("destinoLatitude",-8.3);dados.put("destinoLongitude",-36.0);dados.put("dias",List.of("MONDAY"));dados.put("horarioIda","07:00");
            var rota=mvc.perform(post("/rotas-publicas").header("Authorization","Bearer driver-http").contentType("application/json").content(json.writeValueAsString(dados)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.publicada").value(false)).andReturn();
            long id=json.readTree(rota.getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(get("/rotas-publicas/"+id).header("Authorization","Bearer passenger-http")).andExpect(status().isForbidden());
            mvc.perform(get("/rotas-publicas/catalogo").header("Authorization","Bearer passenger-http"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
            mvc.perform(put("/rotas-publicas/"+id+"/publicacao").header("Authorization","Bearer driver-http").contentType("application/json").content("{\"valor\":true}"))
                    .andExpect(status().isOk());
            var publico=mvc.perform(get("/rotas-publicas/"+id).header("Authorization","Bearer passenger-http"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.motorista").doesNotExist()).andExpect(jsonPath("$.alunos").doesNotExist())
                    .andExpect(jsonPath("$.telefone").doesNotExist()).andReturn();
            assertFalse(publico.getResponse().getContentAsString().contains("private-phone"));
            mvc.perform(get("/rotas-publicas/catalogo").header("Authorization","Bearer passenger-http").param("q","CAMPUS").param("cidade","CARUARU"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.itens[0].telefone").doesNotExist()).andExpect(jsonPath("$.itens[0].latitude").doesNotExist())
                    .andExpect(jsonPath("$.itens[0].passageiros").doesNotExist()).andExpect(jsonPath("$.itens[0].gps").doesNotExist());
            mvc.perform(get("/rotas-publicas/catalogo")).andExpect(status().isUnauthorized());
            mvc.perform(get("/rotas-publicas/catalogo").header("Authorization","Bearer passenger-http").param("tamanho","51"))
                    .andExpect(status().isBadRequest());
            var pedido=mvc.perform(post("/rotas-publicas/"+id+"/solicitacoes").header("Authorization","Bearer passenger-http"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDENTE")).andReturn();
            long vinculo=json.readTree(pedido.getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(put("/turmas/analisar/"+vinculo).param("status","APROVADO").header("Authorization","Bearer driver-http")).andExpect(status().isOk());
            mvc.perform(post("/rotas-publicas/"+id+"/solicitacoes").header("Authorization","Bearer other-http")).andExpect(status().isConflict());
            mvc.perform(get("/turmas/solicitacoes").header("Authorization","Bearer other-http")).andExpect(status().isOk()).andExpect(content().json("[]"));
            mvc.perform(delete("/turmas/solicitacoes/"+vinculo).header("Authorization","Bearer other-http")).andExpect(status().isForbidden());
            mvc.perform(delete("/turmas/solicitacoes/"+vinculo).header("Authorization","Bearer passenger-http")).andExpect(status().isConflict());
        }
    }
}
