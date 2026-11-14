package com.bevans.service;
import java.sql.DriverManager;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Migra schema exclusivo antes de subir o contexto em validate, sem update. */
class OperacaoPostgresIT extends OperacaoIntegrationTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r)throws Exception{
        String schema="operacao_"+UUID.randomUUID().toString().replace("-","");String url="jdbc:postgresql://127.0.0.1:55435/bevans_validation?currentSchema="+schema;
        try(var c=DriverManager.getConnection(url,"postgres","local-test-only")){
            try(var s=c.createStatement()){s.execute("create schema "+schema);s.execute("set search_path to "+schema);}
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/test/resources/sql/main-1a24bb3-schema.sql"));
            ScriptUtils.executeSqlScript(c,new FileSystemResource("scripts/sql/001_viagem_ativa_unica.sql"));
            try(var scripts=Files.list(Path.of("docs/migrations"))){for(var p:scripts.filter(x->x.toString().endsWith(".sql")).sorted().toList())ScriptUtils.executeSqlScript(c,new FileSystemResource(p));}
        }
        r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"postgres");r.add("spring.datasource.password",()->"local-test-only");r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");r.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
}
