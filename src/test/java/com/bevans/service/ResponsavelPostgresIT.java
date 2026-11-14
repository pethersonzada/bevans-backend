package com.bevans.service;
import org.springframework.test.context.*;
/** Schema migrado e validate, reutilizando a preparação isolada existente. */
class ResponsavelPostgresIT extends ResponsavelIntegrationTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r)throws Exception{OperacaoPostgresIT.postgres(r);}
}
