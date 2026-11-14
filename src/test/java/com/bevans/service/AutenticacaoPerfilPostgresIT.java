package com.bevans.service;
import org.springframework.test.context.*;
class AutenticacaoPerfilPostgresIT extends AutenticacaoPerfilIntegrationTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry r)throws Exception{OperacaoPostgresIT.postgres(r);}
}
