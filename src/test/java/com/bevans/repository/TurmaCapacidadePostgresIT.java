package com.bevans.repository;

import com.bevans.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Reexecuta os três testes concorrentes herdados em PostgreSQL real. */
@DataJpaTest(properties={
        "spring.config.import=",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55435/bevans_validation?currentSchema=capacity_service",
        "spring.datasource.username=postgres", "spring.datasource.password=local-test-only",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update", "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class TurmaCapacidadePostgresIT extends TurmaCapacidadeConcorrenciaTest {
    @Test void criaA12B18EditaA15EPreservaBAposReconsulta() {
        Long[] ids=new TransactionTemplate(transactions).execute(tx->{
            Usuario motorista=new Usuario();String chave=java.util.UUID.randomUUID().toString();
            motorista.setNome("Teste");motorista.setTipo("MOTORISTA");motorista.setEmail(chave+"@example.test");motorista.setTelefone(chave);
            usuarios.save(motorista);
            Turma a=new Turma();a.setNome("A");a.setMotorista(motorista);a.setCapacidade(12);
            Turma b=new Turma();b.setNome("B");b.setMotorista(motorista);b.setCapacidade(18);
            return new Long[]{service.criarTurma(a).getId(),service.criarTurma(b).getId()};
        });
        Turma patch=new Turma();patch.setCapacidade(15);service.atualizarTurma(ids[0],patch);
        assertEquals(15,service.consultarVagas(ids[0]).capacidade());assertEquals(18,service.consultarVagas(ids[1]).capacidade());
    }
}
