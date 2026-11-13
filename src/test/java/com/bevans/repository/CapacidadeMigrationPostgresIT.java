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
                s.execute("create table usuarios(id bigint primary key)");
                s.execute("create table turmas(id bigint primary key,nome text)");
                s.execute("create table aluno_turma(id bigint primary key,turma_id bigint references turmas(id),status text)");
                s.execute("create table viagem(id bigint primary key,turma_id bigint references turmas(id))");
                s.execute("insert into turmas values(7,'A'),(8,'B')");
                s.execute("insert into aluno_turma values(11,7,'APROVADO')");
            }
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/001-capacidade-turma.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/002-rotas-publicas.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/003-embarques.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/004-catalogo-indices.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/005-favoritos.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/006-pontos-excepcionais.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/007-posicao-viagem.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/008-excursoes.sql"));
            try(var s=c.createStatement()) {
                try(var rs=s.executeQuery("select count(*) from rotas_publicas")) {assertTrue(rs.next());assertEquals(0,rs.getLong(1));}
                try(var rs=s.executeQuery("select count(*) from solicitacoes_embarque")) {assertTrue(rs.next());assertEquals(0,rs.getLong(1));}
                s.execute("insert into usuarios values(1)");
                s.execute("insert into viagem(id,turma_id) values(20,7)");
                assertEquals(1,s.executeUpdate("insert into posicao_viagem(viagem_id,latitude,longitude,precisao) values(20,-8,-35,8)"));
                assertEquals("23514",assertThrows(SQLException.class,()->s.executeUpdate("update posicao_viagem set latitude=91 where viagem_id=20")).getSQLState());
                assertEquals(1,s.executeUpdate("insert into excursoes(organizador_id,titulo,destino_nome,destino_latitude,destino_longitude,partida_em,criada_em,capacidade) values(1,'Evento','Destino',-8,-35,CURRENT_TIMESTAMP+interval '1 day',CURRENT_TIMESTAMP,12)"));
                assertEquals("23514",assertThrows(SQLException.class,()->s.executeUpdate("update excursoes set capacidade=0")).getSQLState());
                assertEquals("23514",assertThrows(SQLException.class,()->s.executeUpdate("update excursoes set origem_nome='Origem sem pin'")).getSQLState());
                long rotaId;
                try(var rs=s.executeQuery("insert into rotas_publicas(turma_id,nome,cidade,categoria,modo,origem_nome,origem_latitude,origem_longitude,destino_nome,destino_latitude,destino_longitude) values(7,'Teste','Caruaru','OUTROS','FLEXIVEL','Terminal',-8,-35,'Campus',-8.1,-35.1) returning id")) {
                    assertTrue(rs.next());rotaId=rs.getLong(1);
                }
                String inserir="insert into solicitacoes_embarque(rota_id,aluno_id,data_operacao,sentido,nome_ponto,latitude,longitude,status,criado_em) values("+rotaId+",1,CURRENT_DATE,'IDA','Pin',-8.2,-35.2,'PENDENTE',CURRENT_TIMESTAMP)";
                String favorito="insert into rotas_favoritas(usuario_id,rota_id,ativo) values(1,"+rotaId+",true)";
                assertEquals(1,s.executeUpdate(favorito));assertEquals("23505",assertThrows(SQLException.class,()->s.executeUpdate(favorito)).getSQLState());
                s.executeUpdate("update rotas_favoritas set ativo=false where usuario_id=1");
                assertEquals("23505",assertThrows(SQLException.class,()->s.executeUpdate(favorito)).getSQLState());
                assertEquals(1,s.executeUpdate(inserir));
                SQLException duplicado=assertThrows(SQLException.class,()->s.executeUpdate(inserir));assertEquals("23505",duplicado.getSQLState());
                assertEquals(1,s.executeUpdate("update solicitacoes_embarque set status='CANCELADO' where rota_id="+rotaId));
                assertEquals(1,s.executeUpdate(inserir));
                try(var rs=s.executeQuery("select count(*) from solicitacoes_embarque where rota_id="+rotaId)) {assertTrue(rs.next());assertEquals(2,rs.getLong(1));}
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
