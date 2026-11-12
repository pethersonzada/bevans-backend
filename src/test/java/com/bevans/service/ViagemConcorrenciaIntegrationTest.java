package com.bevans.service;

import com.bevans.exception.ConflitoException;
import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.repository.TurmaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "app.firebase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:concorrencia;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.sql.init.mode=never"
})
class ViagemConcorrenciaIntegrationTest {

    @Autowired ViagemService viagemService;
    @Autowired ViagemRepository viagens;
    @Autowired TurmaRepository turmas;
    @Autowired UsuarioRepository usuarios;

    @Test
    void duasRequisicoesSimultaneasCriamSomenteUmaViagem() throws Exception {
        Usuario motorista = new Usuario();
        motorista.setNome("Motorista concorrencia");
        motorista.setEmail("concorrencia@example.test");
        motorista.setTelefone("81999999999");
        motorista.setTipo("MOTORISTA");
        motorista = usuarios.saveAndFlush(motorista);

        Turma turma = new Turma();
        turma.setNome("Turma concorrencia");
        turma.setMotorista(motorista);
        turma = turmas.saveAndFlush(turma);

        final Long turmaId = turma.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);

        try {
            Callable<String> tarefa = () -> {
                preparados.countDown();
                if (!largada.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Largada não liberada");
                }

                try {
                    viagemService.iniciarRota(turmaId, "IDA");
                    return "CRIADA";
                } catch (ConflitoException e) {
                    return "CONFLITO";
                }
            };

            Future<String> primeira = executor.submit(tarefa);
            Future<String> segunda = executor.submit(tarefa);

            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            largada.countDown();

            List<String> resultados = List.of(
                primeira.get(30, TimeUnit.SECONDS),
                segunda.get(30, TimeUnit.SECONDS)
            );

            assertEquals(1, resultados.stream()
                    .filter("CRIADA"::equals).count());

            assertEquals(1, resultados.stream()
                    .filter("CONFLITO"::equals).count());

            assertTrue(viagens.existsByTurmaIdAndStatus(
                    turmaId, "EM_ANDAMENTO"));

        } finally {
            largada.countDown();
            executor.shutdownNow();
        }
    }
}
