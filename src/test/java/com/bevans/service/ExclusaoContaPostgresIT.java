package com.bevans.service;
import org.springframework.test.context.*;
class ExclusaoContaPostgresIT extends ExclusaoContaIntegrationTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r)throws Exception{OperacaoPostgresIT.postgres(r);}
}
