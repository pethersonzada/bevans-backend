package com.bevans.security;

import com.bevans.model.Usuario;
import com.bevans.dto.ExcursaoRequest;
import com.bevans.dto.InscricaoExcursaoRequest;
import com.bevans.service.ExcursaoService;
import com.bevans.service.ExcursaoPassageiroService;
import com.bevans.repository.InscricaoExcursaoRepository;
import java.util.concurrent.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

import com.bevans.service.ConviteExcursaoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

import com.bevans.repository.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=",
    "app.firebase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:passageiro-excursoes-http;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class ExcursaoPassageiroHttpTest {

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired ExcursaoService excursoes;
    @Autowired ConviteExcursaoService convites;
    @Autowired ObjectMapper json;
    @Autowired ExcursaoPassageiroService passageiroService;
    @Autowired InscricaoExcursaoRepository inscricoes;

    private String criarUsuario(String tipo) {
        String uid = UUID.randomUUID().toString();
        Usuario usuario = new Usuario();
        usuario.setNome("Teste");
        usuario.setTelefone(uid);
        usuario.setEmail(uid + "@test.invalid");
        usuario.setFirebaseUid(uid);
        usuario.setTipo(tipo);
        usuarios.saveAndFlush(usuario);
        return uid;
    }

    private void autenticar(String token, String uid,
                            RunnableTeste teste) throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getUid()).thenReturn(uid);
        when(auth.verifyIdToken(token, true)).thenReturn(decoded);

        try (var scope = mockStatic(FirebaseAuth.class)) {
            scope.when(FirebaseAuth::getInstance).thenReturn(auth);
            teste.executar();
        }
    }

    @FunctionalInterface
    interface RunnableTeste {
        void executar() throws Exception;
    }

    @Test
    void bloqueiaRequisicoesSemAutenticacao() throws Exception {
        mvc.perform(get("/passageiro/excursoes"))
            .andExpect(status().isUnauthorized());

        mvc.perform(post("/passageiro/excursoes/vincular"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void motoristaNaoAcessaExcursaoDePassageiro() throws Exception {
        String uid = criarUsuario("MOTORISTA");

        autenticar("motorista-http", uid, () -> {
            mvc.perform(get("/passageiro/excursoes")
                    .header("Authorization", "Bearer motorista-http"))
                .andExpect(status().isForbidden());

            mvc.perform(post("/passageiro/excursoes/vincular")
                    .header("Authorization", "Bearer motorista-http"))
                .andExpect(status().isForbidden());
        });
    }

    @Test
    void passageiroAutenticadoConsultaListaVazia() throws Exception {
        String uid = criarUsuario("PASSAGEIRO");

        autenticar("passageiro-http", uid, () -> {
            mvc.perform(get("/passageiro/excursoes")
                    .header("Authorization", "Bearer passageiro-http"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.itens").isEmpty());
        });
    }

    @Test
    void passageiroNaoVinculaCredencialInvalida() throws Exception {
        String uid = criarUsuario("PASSAGEIRO");

        autenticar("passageiro-invalido", uid, () -> {
            mvc.perform(post("/passageiro/excursoes/vincular")
                    .header("Authorization", "Bearer passageiro-invalido")
                    .header("X-Inscricao", "invalida"))
                .andExpect(status().isNotFound());
        });
    }

    @Test
    void passageiroNaoConsultaInscricaoDeOutraConta() throws Exception {
        String uid = criarUsuario("PASSAGEIRO");

        autenticar("passageiro-isolamento", uid, () -> {
            mvc.perform(get("/passageiro/excursoes/999999")
                    .header("Authorization", "Bearer passageiro-isolamento"))
                .andExpect(status().isNotFound());
        });
    }

    @Test
    void motoristaNaoConsultaDetalhesDeInscricao() throws Exception {
        String uid = criarUsuario("MOTORISTA");

        autenticar("motorista-detalhes", uid, () -> {
            mvc.perform(get("/passageiro/excursoes/999999")
                    .header("Authorization", "Bearer motorista-detalhes"))
                .andExpect(status().isForbidden());
        });
    }


    @Test
    void inscricaoRealFicaIsoladaEntrePassageiros() throws Exception {
        String motorista = criarUsuario("MOTORISTA");
        String passageiroA = criarUsuario("PASSAGEIRO");
        String passageiroB = criarUsuario("PASSAGEIRO");

        var excursao = excursoes.criar(
            motorista,
            new ExcursaoRequest(
                "Teste isolamento", "Destino",
                -8.3, -35.3,
                null, null, null,
                Instant.now().plusSeconds(86400),
                null, 2, null
            )
        );

        excursao = excursoes.publicar(
            excursao.id(), motorista, excursao.versao()
        );

        var convite = convites.emitir(
            excursao.id(), motorista,
            Instant.now().plusSeconds(3600)
        );

        var credencial = mvc.perform(
            post("/publico/excursoes/credenciais")
                .header("X-Convite", convite.token())
        ).andExpect(status().isOk()).andReturn();

        String token = json.readTree(
            credencial.getResponse().getContentAsString()
        ).get("token").asText();

        var dados = new InscricaoExcursaoRequest(
            "Participante", "contato@test.invalid",
            null, "Meu pin", -8.12, -35.12,
            true, true
        );

        var resposta = mvc.perform(
            post("/publico/excursoes/inscricoes")
                .header("X-Convite", convite.token())
                .header("X-Inscricao", token)
                .contentType("application/json")
                .content(json.writeValueAsString(dados))
        ).andExpect(status().isOk()).andReturn();

        long inscricaoId = json.readTree(
            resposta.getResponse().getContentAsString()
        ).get("id").asLong();

        autenticar("token-passageiro-a", passageiroA, () -> {
            mvc.perform(post("/passageiro/excursoes/vincular")
                    .header("Authorization", "Bearer token-passageiro-a")
                    .header("X-Inscricao", token))
                .andExpect(status().isNoContent());

            mvc.perform(get("/passageiro/excursoes/" + inscricaoId)
                    .header("Authorization", "Bearer token-passageiro-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscricaoId").value(inscricaoId));

            mvc.perform(get("/passageiro/excursoes")
                    .header("Authorization", "Bearer token-passageiro-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        });

        autenticar("token-passageiro-b", passageiroB, () -> {
            mvc.perform(get("/passageiro/excursoes/" + inscricaoId)
                    .header("Authorization", "Bearer token-passageiro-b"))
                .andExpect(status().isNotFound());

            mvc.perform(get("/passageiro/excursoes")
                    .header("Authorization", "Bearer token-passageiro-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

            mvc.perform(post("/passageiro/excursoes/vincular")
                    .header("Authorization", "Bearer token-passageiro-b")
                    .header("X-Inscricao", token))
                .andExpect(status().isConflict());
        });
    }


    @Test
    void vinculacoesSimultaneasNaoTrocamProprietario() throws Exception {
        String motorista = criarUsuario("MOTORISTA");
        String passageiroA = criarUsuario("PASSAGEIRO");
        String passageiroB = criarUsuario("PASSAGEIRO");

        var excursao = excursoes.criar(
            motorista,
            new ExcursaoRequest(
                "Concorrencia", "Destino", -8.3, -35.3,
                null, null, null,
                Instant.now().plusSeconds(86400),
                null, 2, null
            )
        );
        excursao = excursoes.publicar(
            excursao.id(), motorista, excursao.versao()
        );

        var convite = convites.emitir(
            excursao.id(), motorista,
            Instant.now().plusSeconds(3600)
        );

        var credencial = mvc.perform(
            post("/publico/excursoes/credenciais")
                .header("X-Convite", convite.token())
        ).andExpect(status().isOk()).andReturn();

        String token = json.readTree(
            credencial.getResponse().getContentAsString()
        ).get("token").asText();

        var dados = new InscricaoExcursaoRequest(
            "Concorrente", "concorrente@test.invalid",
            null, "Meu pin", -8.12, -35.12,
            true, true
        );

        var resposta = mvc.perform(
            post("/publico/excursoes/inscricoes")
                .header("X-Convite", convite.token())
                .header("X-Inscricao", token)
                .contentType("application/json")
                .content(json.writeValueAsString(dados))
        ).andExpect(status().isOk()).andReturn();

        long inscricaoId = json.readTree(
            resposta.getResponse().getContentAsString()
        ).get("id").asLong();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);

        try {
            java.util.function.Function<String, Callable<String>> tarefa =
                uid -> () -> {
                    preparados.countDown();
                    if (!largada.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timeout na largada");
                    }
                    try {
                        passageiroService.vincular(uid, token);
                        return "VINCULADO";
                    } catch (ResponseStatusException ex) {
                        if (ex.getStatusCode().value() == 409) {
                            return "CONFLITO";
                        }
                        throw ex;
                    }
                };

            Future<String> a = executor.submit(tarefa.apply(passageiroA));
            Future<String> b = executor.submit(tarefa.apply(passageiroB));

            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            largada.countDown();

            String resultadoA = a.get(30, TimeUnit.SECONDS);
            String resultadoB = b.get(30, TimeUnit.SECONDS);

            assertEquals(1, java.util.stream.Stream.of(resultadoA, resultadoB)
                .filter("VINCULADO"::equals).count());

            assertEquals(1, java.util.stream.Stream.of(resultadoA, resultadoB)
                .filter("CONFLITO"::equals).count());

            var inscricao = inscricoes.findById(inscricaoId).orElseThrow();
            assertNotNull(inscricao.getPassageiro());

            String vencedor = "VINCULADO".equals(resultadoA)
                ? passageiroA : passageiroB;

            assertEquals(
                usuarios.findByFirebaseUid(vencedor).orElseThrow().getId(),
                inscricao.getPassageiro().getId()
            );
        } finally {
            largada.countDown();
            executor.shutdownNow();
        }
    }

}
