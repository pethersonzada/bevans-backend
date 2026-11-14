package com.bevans.repository;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;
class FirebaseUidMigrationPostgresIT {
    @Test void duplicidadeInterrompeMigracaoSemApagarOuAssociarPerfis()throws Exception{
        String schema="uid_guard_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55435/bevans_validation?currentSchema="+schema,"postgres","local-test-only")){
            try(var s=c.createStatement()){s.execute("create schema "+schema);s.execute("set search_path to "+schema);}
            ScriptUtils.executeSqlScript(c,new FileSystemResource("src/test/resources/sql/main-1a24bb3-schema.sql"));
            try(var s=c.createStatement()){s.execute("insert into usuarios(id,nome,email,telefone,tipo,firebase_uid) values(11,'A','a@test.invalid','teste-a','PASSAGEIRO','uid-sintetico'),(12,'B','b@test.invalid','teste-b','PASSAGEIRO','uid-sintetico')");}
            var erro=assertThrows(org.springframework.jdbc.datasource.init.ScriptStatementFailedException.class,()->ScriptUtils.executeSqlScript(c,new FileSystemResource("docs/migrations/019-identidade-firebase-unica.sql")));
            assertEquals("23514",((SQLException)erro.getCause()).getSQLState());
            try(var s=c.createStatement()){s.execute("ROLLBACK");try(var rs=s.executeQuery("select count(*) from usuarios where id in(11,12) and firebase_uid='uid-sintetico'")){assertTrue(rs.next());assertEquals(2,rs.getInt(1));}try(var rs=s.executeQuery("select to_regclass('uq_usuarios_firebase_uid')")){assertTrue(rs.next());assertNull(rs.getString(1));}}
        }
    }
}
