package com.bevans.service;
import org.springframework.test.context.*;
class ContatoMotoristaPostgresIT extends ContatoMotoristaIntegrationTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r)throws Exception{OperacaoPostgresIT.postgres(r);}
}
