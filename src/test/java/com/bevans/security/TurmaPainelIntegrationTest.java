package com.bevans.security;

import com.bevans.model.Usuario;
import com.bevans.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "app.firebase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:turma-painel",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class TurmaPainelIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;

    private Usuario cadastrar(FirebaseAuth auth, String uid, String tipo) throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNome(uid);
        usuario.setFirebaseUid(uid);
        usuario.setEmail(uid + "@example.test");
        usuario.setTelefone(uid);
        usuario.setTipo(tipo);
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(auth.verifyIdToken(uid, true)).thenReturn(token);
        return usuarios.saveAndFlush(usuario);
    }

    private JsonNode resposta(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void conviteDoPainelSoVinculaPassageiroAposAprovacao() throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        Usuario motorista = cadastrar(auth, "motorista", "MOTORISTA");
        Usuario passageiro = cadastrar(auth, "passageiro", "PASSAGEIRO");
        try (var sdk = mockStatic(FirebaseAuth.class)) {
            sdk.when(FirebaseAuth::getInstance).thenReturn(auth);
            mvc.perform(get("/usuarios/por-uid/passageiro").header("Authorization", "Bearer passageiro"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firebaseUid").value("passageiro"))
                .andExpect(jsonPath("$.tipo").value("PASSAGEIRO"));
            String cadastro = """
                {"nome":"Campus","turno":"NOITE","capacidade":3,"motorista":{"id":%d}}
                """.formatted(motorista.getId());
            JsonNode turma = resposta(mvc.perform(post("/turmas").header("Authorization", "Bearer motorista")
                .contentType("application/json").content(cadastro))
                .andExpect(jsonPath("$.codigoConvite").isNotEmpty())
                .andExpect(jsonPath("$.id").isNumber()));
            long turmaId = turma.get("id").longValue();
            var ingresso = post("/turmas/entrar/{id}", passageiro.getId())
                .param("codigo", turma.get("codigoConvite").textValue()).header("Authorization", "Bearer passageiro");
            mvc.perform(ingresso).andExpect(status().isOk());
            mvc.perform(ingresso).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Sua solicitação já está pendente."));
            var minhasTurmas = get("/turmas/usuario/{id}/todas", passageiro.getId())
                .header("Authorization", "Bearer passageiro");
            mvc.perform(minhasTurmas).andExpect(status().isOk()).andExpect(content().json("[]"));
            JsonNode pendentes = resposta(mvc.perform(get("/turmas/{id}/pendentes", turmaId)
                .header("Authorization", "Bearer motorista"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].aluno.nome").value("passageiro")));
            mvc.perform(put("/turmas/analisar/{id}", pendentes.get(0).get("id").longValue())
                .param("status", "APROVADO").header("Authorization", "Bearer motorista"))
                .andExpect(status().isOk());
            mvc.perform(minhasTurmas).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(turmaId));
            mvc.perform(get("/turmas/{id}/passageiros", turmaId).header("Authorization", "Bearer motorista"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(passageiro.getId()));
        }
    }
}
