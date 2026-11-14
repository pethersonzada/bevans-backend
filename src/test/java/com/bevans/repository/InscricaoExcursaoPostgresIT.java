package com.bevans.repository;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
@DataJpaTest(properties={"spring.config.import=",
 "spring.datasource.url=jdbc:postgresql://127.0.0.1:55435/bevans_validation?currentSchema=enrollment_service",
 "spring.datasource.username=postgres","spring.datasource.password=local-test-only",
 "spring.datasource.driver-class-name=org.postgresql.Driver","spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
 "spring.jpa.hibernate.ddl-auto=update","spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class InscricaoExcursaoPostgresIT extends InscricaoExcursaoIntegrationTest {}
