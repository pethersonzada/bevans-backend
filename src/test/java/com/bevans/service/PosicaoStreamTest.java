package com.bevans.service;
import com.bevans.dto.PosicaoDTO;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosicaoStreamTest {
    private static class Tempo extends Clock {
        Instant agora=Instant.parse("2026-11-13T12:00:00Z");
        public Instant instant(){return agora;}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId z){return this;}
    }
    private PosicaoDTO dto(String estado){return new PosicaoDTO(1L,estado,null,null,null,null,null,null,10);}
    @Test void limitaConexoesEExpiraSemGuardarToken(){
        var gps=mock(PosicaoViagemService.class);var clock=new Tempo();var s=new PosicaoStreamService(gps,clock);
        when(gps.consultar(1L,"uid")).thenReturn(dto("SEM_LOCALIZACAO"));
        assertThrows(ResponseStatusException.class,()->s.abrir(1L,"uid",null));
        s.abrir(1L,"uid",clock.instant().plusSeconds(20));s.abrir(1L,"uid",clock.instant().plusSeconds(20));
        assertEquals(429,assertThrows(ResponseStatusException.class,()->s.abrir(1L,"uid",clock.instant().plusSeconds(20))).getStatusCode().value());
        clock.agora=clock.agora.plusSeconds(20);s.atualizar();assertEquals(0,s.quantidade());
    }
    @Test void revalidaPermissaoEPermiteReconectarAposRevogacao(){
        var gps=mock(PosicaoViagemService.class);var clock=new Tempo();var s=new PosicaoStreamService(gps,clock);
        when(gps.consultar(1L,"uid")).thenReturn(dto("ATUALIZADA"));s.abrir(1L,"uid",clock.instant().plusSeconds(600));
        when(gps.consultar(1L,"uid")).thenThrow(new AccessDeniedException("Revogado"));s.atualizar();assertEquals(0,s.quantidade());
        doReturn(dto("DESATUALIZADA")).when(gps).consultar(1L,"uid");s.abrir(1L,"uid",clock.instant().plusSeconds(600));assertEquals(1,s.quantidade());
        when(gps.consultar(1L,"uid")).thenReturn(dto("FINALIZADA"));s.atualizar();assertEquals(0,s.quantidade());
    }
    @Test void contextosCompartilhamLimiteERevalidamCredencialSemGuardarSegredo(){
        var gps=mock(PosicaoViagemService.class);var clock=new Tempo();var s=new PosicaoStreamService(gps,clock);
        var permitido=new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.function.Supplier<PosicaoStreamService.Atualizacao> consulta=()->{if(!permitido.get())throw new AccessDeniedException("Revogada");return new PosicaoStreamService.Atualizacao("ATUALIZADA",java.util.Map.of("execucaoId",2));};
        s.abrirContexto("C:9",clock.instant().plusSeconds(600),consulta);
        s.abrirContexto("C:9",clock.instant().plusSeconds(600),consulta);
        assertThrows(ResponseStatusException.class,()->s.abrirContexto("C:9",clock.instant().plusSeconds(600),consulta));
        permitido.set(false);s.atualizar();assertEquals(0,s.quantidade());
    }
}
