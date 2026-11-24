package com.bevans.service;

import com.bevans.security.TurmaPainelIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class TurmaPainelPostgresIT extends TurmaPainelIntegrationTest {
    @DynamicPropertySource
    static void prepararBancoMigrado(DynamicPropertyRegistry propriedades) throws Exception {
        OperacaoPostgresIT.postgres(propriedades);
    }
}
