package com.bevans.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Smoke test em H2 PostgreSQL mode; não substitui validação PostgreSQL. */
class CapacidadeMigrationTest {
    @Test void scriptPreservaIdsDadosLegadosEValidaNovosValores() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:capacity-migration;MODE=PostgreSQL")) {
            try(var s=c.createStatement()) {
                s.execute("create table turmas(id bigint primary key, nome varchar(255))");
                s.execute("create table aluno_turma(id bigint primary key, turma_id bigint, status varchar(32))");
                s.execute("insert into turmas values(7,'Turma existente'),(8,'Outra turma')");
                s.execute("insert into aluno_turma values(11,7,'APROVADO')");
            }
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/001-capacidade-turma.sql"));
            try(var s=c.createStatement()) {
                try(var rs=s.executeQuery("select nome,capacidade from turmas where id=7")) {
                    assertTrue(rs.next()); assertEquals("Turma existente",rs.getString(1)); assertNull(rs.getObject(2));
                }
                try(var rs=s.executeQuery("select turma_id from aluno_turma where id=11")) {
                    assertTrue(rs.next()); assertEquals(7,rs.getLong(1));
                }
                assertThrows(SQLException.class,()->s.executeUpdate("update turmas set capacidade=0 where id=7"));
                assertEquals(1,s.executeUpdate("update turmas set capacidade=15 where id=7"));
                try(var rs=s.executeQuery("select capacidade from turmas where id=8")) {
                    assertTrue(rs.next()); assertNull(rs.getObject(1));
                }
            }
        }
    }
}
