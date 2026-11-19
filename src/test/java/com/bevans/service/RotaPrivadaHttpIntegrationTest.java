package com.bevans.service;

import com.bevans.model.*;
import com.bevans.repository.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=",
    "app.firebase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:rota_privada_http_59;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class RotaPrivadaHttpIntegrationTest {
    private static final String TOKEN = "token-ficticio-issue-59";

    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @Autowired UsuarioRepository usuarios;
    @Autowired TurmaRepository turmas;
    @Autowired AlunoTurmaRepository vinculos;
    @Autowired EnderecoRepository enderecos;
    @Autowired PresencaRepository presencas;
    @Autowired ViagemRepository viagens;
    @Autowired OperacaoViagemRepository operacoes;

    private record Cenario(Turma turma, Usuario motorista, Usuario passageiro) {}

    private Usuario usuario(String tipo) {
        String id = UUID.randomUUID().toString();
        Usuario usuario = new Usuario();
        usuario.setNome(id);
        usuario.setTelefone(id);
        usuario.setEmail(id + "@test.invalid");
        usuario.setFirebaseUid(id);
        usuario.setTipo(tipo);
        return usuarios.saveAndFlush(usuario);
    }

    private Cenario cenario(boolean registrarPresenca, boolean enderecoIdaValido) {
        Usuario motorista = usuario("MOTORISTA");
        Usuario passageiro = usuario("PASSAGEIRO");

        Turma turma = new Turma();
        turma.setNome("Turma teste HTTP 59");
        turma.setMotorista(motorista);
        turma.setCapacidade(12);
        turma.setOrigemNome("Origem ficticia");
        turma.setOrigemLatitude(-8.0);
        turma.setOrigemLongitude(-35.0);
        turma.setDestinoNome("Destino ficticio");
        turma.setDestinoLatitude(-8.3);
        turma.setDestinoLongitude(-35.3);
        turma = turmas.saveAndFlush(turma);

        AlunoTurma vinculo = new AlunoTurma();
        vinculo.setTurma(turma);
        vinculo.setAluno(passageiro);
        vinculo.setStatus("APROVADO");
        vinculos.saveAndFlush(vinculo);

        if (registrarPresenca) {
            Endereco ida = new Endereco();
            ida.setUsuario(passageiro);
            ida.setLatitude(-8.1);
            ida.setLongitude(-35.1);
            ida = enderecos.saveAndFlush(ida);

            Endereco volta = new Endereco();
            volta.setUsuario(passageiro);
            volta.setLatitude(-8.2);
            volta.setLongitude(-35.2);
            volta = enderecos.saveAndFlush(volta);

            Presenca presenca = new Presenca();
            presenca.setTurma(turma);
            presenca.setUsuario(passageiro);
            presenca.setData(LocalDate.now(clock));
            presenca.setStatus("IDA");
            presenca.setEnderecoIda(enderecoIdaValido ? ida : null);
            presenca.setEnderecoVolta(volta);
            presenca.setEndereco(volta); // Legado nao deve substituir o endereco da IDA.
            presencas.saveAndFlush(presenca);
        }

        return new Cenario(turma, motorista, passageiro);
    }

    private MockedStatic<FirebaseAuth> firebase(String uid) throws Exception {
        FirebaseAuth auth = mock(FirebaseAuth.class);
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getClaims()).thenReturn(Map.of());
        when(auth.verifyIdToken(TOKEN, true)).thenReturn(token);
        MockedStatic<FirebaseAuth> mock = mockStatic(FirebaseAuth.class);
        mock.when(FirebaseAuth::getInstance).thenReturn(auth);
        return mock;
    }

    private String corpoInicio(Long turmaId) {
        return "{\"turmaId\":" + turmaId
            + ",\"sentido\":\"IDA\",\"modalidade\":\"PRIVADA\"}";
    }

    @Test
    void inicioPrivadoHttpMantemPassageiroNoRoteiroEPainel() throws Exception {
        Cenario c = cenario(true, true);
        String uid = c.motorista().getFirebaseUid();

        try (var ignored = firebase(uid)) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer " + TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoInicio(c.turma().getId())))
                .andExpect(status().isOk());

            Long viagemId = viagens.findFirstByTurmaIdAndStatus(
                c.turma().getId(), "EM_ANDAMENTO"
            ).orElseThrow().getId();

            mvc.perform(get("/operacoes/VIAGEM/{id}/roteiro", viagemId)
                    .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].tipo").value("ORIGEM"))
                .andExpect(jsonPath("$[1].tipo").value("INDIVIDUAL"))
                .andExpect(jsonPath("$[1].passageiros[0].id")
                    .value(c.passageiro().getId().intValue()))
                .andExpect(jsonPath("$[2].tipo").value("DESTINO"));

            mvc.perform(get("/operacoes/VIAGEM/{id}", viagemId)
                    .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contadores.esperados").value(1))
                .andExpect(jsonPath("$.registros[0].alunoId")
                    .value(c.passageiro().getId().intValue()));

            var requisicaoAssincrona = mvc.perform(get("/rota/otimizar")
                    .param("turmaId", c.turma().getId().toString())
                    .param("sentido", "IDA")
                    .header("Authorization", "Bearer " + TOKEN))
                .andExpect(request().asyncStarted())
                .andReturn();

            mvc.perform(asyncDispatch(requisicaoAssincrona))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id")
                    .value(c.passageiro().getId().intValue()));
        }
    }

    @Test
    void enderecoDaIdaAusenteRetorna409ESemViagemParcial() throws Exception {
        Cenario c = cenario(true, false);
        long antes = operacoes.count();

        try (var ignored = firebase(c.motorista().getFirebaseUid())) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer " + TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoInicio(c.turma().getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PONTO_INVALIDO"));
        }

        assertTrue(viagens.findFirstByTurmaIdAndStatus(
            c.turma().getId(), "EM_ANDAMENTO"
        ).isEmpty());
        assertEquals(antes, operacoes.count());
    }

    @Test
    void vinculoAprovadoSemPresencaDiariaBloqueiaInicio() throws Exception {
        Cenario c = cenario(false, false);

        try (var ignored = firebase(c.motorista().getFirebaseUid())) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer " + TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoInicio(c.turma().getId())))
                .andExpect(status().isConflict());
        }

        assertTrue(viagens.findFirstByTurmaIdAndStatus(
            c.turma().getId(), "EM_ANDAMENTO"
        ).isEmpty());
    }

    @Test
    void rotaPrivadaRecusaTokenAusenteEMotoristaAlheio() throws Exception {
        Cenario c = cenario(true, true);

        mvc.perform(post("/rota/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoInicio(c.turma().getId())))
            .andExpect(status().isUnauthorized());

        Usuario outro = usuario("MOTORISTA");
        try (var ignored = firebase(outro.getFirebaseUid())) {
            mvc.perform(post("/rota/iniciar")
                    .header("Authorization", "Bearer " + TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoInicio(c.turma().getId())))
                .andExpect(status().isForbidden());
        }

        assertTrue(viagens.findFirstByTurmaIdAndStatus(
            c.turma().getId(), "EM_ANDAMENTO"
        ).isEmpty());
    }
}
