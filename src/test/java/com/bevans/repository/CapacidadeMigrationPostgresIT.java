package com.bevans.repository;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

/** Apenas o PostgreSQL descartável da porta 55435; não lê .env. */
class CapacidadeMigrationPostgresIT {
    @Test void migracaoRealPreservaIdsRelacionamentosECapacidadeLegada() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55435/bevans_validation", "postgres", "local-test-only")) {
            String schema="migration_"+java.util.UUID.randomUUID().toString().replace("-","");
            try(var s=c.createStatement()) {
                s.execute("create schema "+schema);
                s.execute("set search_path to "+schema);
                s.execute("create table turmas(id bigint primary key,nome text)");
                s.execute("create table aluno_turma(id bigint primary key,turma_id bigint references turmas(id),status text)");
                s.execute("insert into turmas values(7,'A'),(8,'B')");
                s.execute("insert into aluno_turma values(11,7,'APROVADO')");
            }
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/001-capacidade-turma.sql"));
            try(var s=c.createStatement()) {
                try(var rs=s.executeQuery("select t.id,t.capacidade,a.id from turmas t join aluno_turma a on t.id=a.turma_id")) {
                    assertTrue(rs.next());assertEquals(7,rs.getLong(1));assertNull(rs.getObject(2));assertEquals(11,rs.getLong(3));
                }
                assertThrows(SQLException.class,()->s.executeUpdate("update turmas set capacidade=0 where id=7"));
                assertEquals(1,s.executeUpdate("update turmas set capacidade=12 where id=7"));
                assertEquals(1,s.executeUpdate("update turmas set capacidade=18 where id=8"));
                assertEquals(1,s.executeUpdate("update turmas set capacidade=15 where id=7"));
                try(var rs=s.executeQuery("select capacidade from turmas where id=8")) {assertTrue(rs.next());assertEquals(18,rs.getInt(1));}
            }
        }
    }
}
