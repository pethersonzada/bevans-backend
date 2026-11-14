package com.bevans.service;
import com.bevans.dto.PosicaoRequest;
import com.bevans.model.PosicaoExecucaoExcursao;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class RegrasGpsTest {
    private final Instant agora=Instant.parse("2026-11-14T12:00:00Z");
    private Clock clock(Instant i){return Clock.fixed(i,ZoneOffset.UTC);}
    private PosicaoRequest amostra(String id,Instant instante){return new PosicaoRequest(id,-8.1,-35.1,instante,5.0,null);}
    @Test void retryNaoRenovaTtlEExpiracaoEscondeEstadoAtual(){
        var p=new PosicaoExecucaoExcursao();p.atualizar(1L,amostra("um",agora),agora);
        assertFalse(RegrasGps.validar(amostra("um",agora),p,agora,clock(agora.plusSeconds(40))).nova());
        assertEquals("ATUALIZADA",RegrasGps.estado(true,p,clock(agora.plusSeconds(29))));
        assertEquals("DESATUALIZADA",RegrasGps.estado(true,p,clock(agora.plusSeconds(30))));
        assertEquals("FINALIZADA",RegrasGps.estado(false,p,clock(agora)));
    }
    @Test void rejeitaAmostraReutilizadaForaDeOrdemAntigaEPrecoce(){
        var p=new PosicaoExecucaoExcursao();p.atualizar(1L,amostra("um",agora),agora);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->RegrasGps.validar(amostra("um",agora.plusSeconds(1)),p,agora,clock(agora))).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->RegrasGps.validar(amostra("dois",agora),p,agora,clock(agora.plusSeconds(5)))).getStatusCode().value());
        assertEquals(429,assertThrows(ResponseStatusException.class,()->RegrasGps.validar(amostra("dois",agora.plusSeconds(1)),p,agora,clock(agora.plusSeconds(1)))).getStatusCode().value());
        assertThrows(IllegalArgumentException.class,()->RegrasGps.validar(amostra("dois",agora.minusSeconds(31)),new PosicaoExecucaoExcursao(),null,clock(agora)));
        assertThrows(IllegalArgumentException.class,()->RegrasGps.validar(amostra("dois",agora.plusSeconds(6)),new PosicaoExecucaoExcursao(),null,clock(agora)));
    }
}
