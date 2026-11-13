package com.bevans.repository;

import com.bevans.model.Turma;
import com.bevans.model.Usuario;
import com.bevans.service.GeocodingService;
import com.bevans.service.TurmaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
@Import(TurmaService.class)
class TurmaCadastroIntegrationTest {
    @Autowired TestEntityManager em;
    @Autowired TurmaService service;
    @MockBean GeocodingService geocoding;

    @Test void rascunhoSalvaPercursoEmDuasEtapasEPreservadoAposReabrir() {
        Usuario motorista = new Usuario();
        motorista.setNome("Motorista de teste");
        motorista.setEmail("rascunho@example.test");
        motorista.setTelefone("rascunho-test");
        motorista.setTipo("MOTORISTA");
        em.persist(motorista);
        Turma rascunho = new Turma();
        rascunho.setMotorista(motorista);
        rascunho.setNome("Faculdade");
        rascunho.setTurno("NOITE");
        Long id = service.criarTurma(rascunho).getId();
        em.flush(); em.clear();
        assertNull(em.find(Turma.class, id).getOrigemLatitude());

        Turma origem = new Turma();
        origem.setOrigemNome("Garagem");
        origem.setOrigemLatitude(-8.23);
        origem.setOrigemLongitude(-35.97);
        service.atualizarTurma(id, origem);
        em.flush(); em.clear();
        Turma destino = new Turma();
        destino.setDestinoNome("Campus");
        destino.setDestinoLatitude(-8.30);
        destino.setDestinoLongitude(-35.99);
        service.atualizarTurma(id, destino);
        em.flush(); em.clear();

        Turma reaberta = em.find(Turma.class, id);
        assertEquals("Garagem", reaberta.getOrigemNome());
        assertEquals(-8.23, reaberta.getOrigemLatitude());
        assertEquals(-35.97, reaberta.getOrigemLongitude());
        assertEquals("Campus", reaberta.getDestinoNome());
        assertEquals(-8.30, reaberta.getDestinoLatitude());
        assertEquals(-35.99, reaberta.getDestinoLongitude());
    }
}
