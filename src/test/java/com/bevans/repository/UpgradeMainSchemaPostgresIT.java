package com.bevans.repository;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static org.junit.jupiter.api.Assertions.*;

/** Upgrade completo em PG local, seguido de validate (nao update) de todos os modelos. */
class UpgradeMainSchemaPostgresIT {
    @Test void mainComDadosAceitaMigracoesEMapeamentoSemDdlAutomatico() throws Exception {
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        String url="jdbc:postgresql://127.0.0.1:55435/bevans_validation?currentSchema="+schema;
        var ds=new DriverManagerDataSource(url,"postgres","local-test-only");
        try(var c=ds.getConnection()){
            try(var s=c.createStatement()){s.execute("create schema "+schema);s.execute("set search_path to "+schema);}
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/test/resources/sql/main-1a24bb3-schema.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("scripts/sql/001_viagem_ativa_unica.sql"));
            try(var s=c.createStatement()){
                s.execute("insert into usuarios(id,nome,telefone,email,tipo) values(1,'Legado','fixture-1','fixture@example.invalid','MOTORISTA')");
                s.execute("insert into turmas(id,motorista_id,nome) values(7,1,'Legada')");
                s.execute("insert into aluno_turma(id,turma_id,aluno_id,status) values(11,7,1,'APROVADO')");
                s.execute("insert into viagem(id,turma_id,sentido,status) values(20,7,'IDA','EM_ANDAMENTO')");
                assertEquals("23505",assertThrows(SQLException.class,()->s.execute("insert into viagem(id,turma_id,sentido,status) values(21,7,'VOLTA','EM_ANDAMENTO')")).getSQLState());
            }
            try(var paths=Files.list(Path.of("docs/migrations"))){
                var scripts=paths.filter(p->p.toString().endsWith(".sql")).sorted().toList();assertEquals(14,scripts.size());
                for(Path script:scripts)ScriptUtils.executeSqlScript(c,new FileSystemResource(script));
            }
            try(var s=c.createStatement();var rs=s.executeQuery("select t.id,t.capacidade,a.id,v.id,v.iniciada_em from turmas t join aluno_turma a on a.turma_id=t.id join viagem v on v.turma_id=t.id")){
                assertTrue(rs.next());assertEquals(7,rs.getLong(1));assertNull(rs.getObject(2));assertEquals(11,rs.getLong(3));assertEquals(20,rs.getLong(4));assertNull(rs.getObject(5));
            }
            try(var s=c.createStatement()){
                s.execute("insert into excursoes(id,organizador_id,titulo,destino_nome,destino_latitude,destino_longitude,partida_em,criada_em,capacidade) values(99,1,'Evento','Destino',-8,-35,CURRENT_TIMESTAMP+interval '1 day',CURRENT_TIMESTAMP,2)");
                s.execute("insert into credenciais_inscricao_excursao(id,excursao_id,token_hash,expira_em) values(88,99,repeat('a',64),CURRENT_TIMESTAMP+interval '1 day'),(89,99,repeat('b',64),CURRENT_TIMESTAMP+interval '1 day')");
                String inserir="insert into inscricoes_excursao(excursao_id,credencial_id,nome,contato,participante_hash,nome_ponto,latitude,longitude,estado,reserva_valida_ate,consentimento_em) values(99,%d,'Fixture','fixture@example.invalid',repeat('c',64),'Pin',-8,-35,'SOLICITADA',CURRENT_TIMESTAMP+interval '15 minutes',CURRENT_TIMESTAMP)";
                s.executeUpdate(inserir.formatted(88));assertEquals("23505",assertThrows(SQLException.class,()->s.executeUpdate(inserir.formatted(89))).getSQLState());
                s.executeUpdate("update inscricoes_excursao set estado='CANCELADA' where excursao_id=99");assertEquals(1,s.executeUpdate(inserir.formatted(89)));
                assertEquals("23514",assertThrows(SQLException.class,()->s.executeUpdate("update inscricoes_excursao set latitude=91 where excursao_id=99")).getSQLState());
                String execucao="insert into execucoes_excursao(excursao_id,sentido,estado,iniciada_em,roteiro_json) values(99,'%s','EM_ANDAMENTO',CURRENT_TIMESTAMP,'[]')";
                s.executeUpdate(execucao.formatted("IDA"));assertEquals("23505",assertThrows(SQLException.class,()->s.executeUpdate(execucao.formatted("VOLTA"))).getSQLState());
                s.executeUpdate("update execucoes_excursao set estado='FINALIZADA' where excursao_id=99");assertEquals(1,s.executeUpdate(execucao.formatted("VOLTA")));
                assertEquals("23505",assertThrows(SQLException.class,()->s.executeUpdate(execucao.formatted("IDA"))).getSQLState());
            }
        }
        var factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);factory.setPackagesToScan("com.bevans.model");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try{factory.afterPropertiesSet();assertNotNull(factory.getObject());}finally{factory.destroy();}
    }
}
