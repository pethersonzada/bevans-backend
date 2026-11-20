package com.bevans.security;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class LimiteConvitesTest {
    @Test void limitaMesmoComForwardedForForjadoESemConsumirRotasPrivadas() throws Exception {
        var filtro=new LimiteConvitesFilter(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC));var chamadas=new AtomicInteger();
        for(int i=0;i<61;i++){
            var r=new MockHttpServletRequest("GET","/publico/excursoes/convite");r.setRemoteAddr("127.0.0.1");r.addHeader("X-Forwarded-For","cliente-"+i);
            var s=new MockHttpServletResponse();filtro.doFilter(r,s,(a,b)->chamadas.incrementAndGet());
            if(i==60){assertEquals(429,s.getStatus());assertEquals("60",s.getHeader("Retry-After"));}
        }
        assertEquals(60,chamadas.get());
        filtro.doFilter(new MockHttpServletRequest("GET","/excursoes"),new MockHttpServletResponse(),(a,b)->chamadas.incrementAndGet());assertEquals(61,chamadas.get());
    }
    @Test void proxyConfiavelSeparaDoisVisitantesSemAceitarXForwardedFor() throws Exception {
        var filtro = new LimiteConvitesFilter(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
                java.util.Set.of("127.0.0.1"));
        var chamadas = new AtomicInteger();
        for (int i = 0; i < 61; i++) {
            var r = new MockHttpServletRequest("GET", "/publico/excursoes/convite");
            r.setRemoteAddr("127.0.0.1");
            r.addHeader("X-Real-IP", "198.51.100.10");
            r.addHeader("X-Forwarded-For", "203.0.113." + i);
            var s = new MockHttpServletResponse();
            filtro.doFilter(r, s, (a, b) -> chamadas.incrementAndGet());
            if (i == 60) assertEquals(429, s.getStatus());
        }
        for (int i = 0; i < 60; i++) {
            var r = new MockHttpServletRequest("GET", "/publico/excursoes/convite");
            r.setRemoteAddr("127.0.0.1");
            r.addHeader("X-Real-IP", "198.51.100.11");
            var s = new MockHttpServletResponse();
            filtro.doFilter(r, s, (a, b) -> chamadas.incrementAndGet());
            assertEquals(200, s.getStatus());
        }
        assertEquals(120, chamadas.get());
    }

    @Test void clienteDiretoNaoPodeForjarXRealIp() throws Exception {
        var filtro = new LimiteConvitesFilter(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
                java.util.Set.of("127.0.0.1"));
        var chamadas = new AtomicInteger();
        for (int i = 0; i < 61; i++) {
            var r = new MockHttpServletRequest("GET", "/publico/excursoes/convite");
            r.setRemoteAddr("203.0.113.8");
            r.addHeader("X-Real-IP", "198.51.100." + i);
            var s = new MockHttpServletResponse();
            filtro.doFilter(r, s, (a, b) -> chamadas.incrementAndGet());
            if (i == 60) assertEquals(429, s.getStatus());
        }
        assertEquals(60, chamadas.get());
    }

    @Test void cabecalhoMalformadoOuDuplicadoNaoDivideCota() throws Exception {
        var filtro = new LimiteConvitesFilter(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
                java.util.Set.of("127.0.0.1"));
        var chamadas = new AtomicInteger();
        for (int i = 0; i < 61; i++) {
            var r = new MockHttpServletRequest("GET", "/publico/excursoes/convite");
            r.setRemoteAddr("127.0.0.1");
            r.addHeader("X-Real-IP", "198.51.100." + i + ", 203.0.113.1");
            var s = new MockHttpServletResponse();
            filtro.doFilter(r, s, (a, b) -> chamadas.incrementAndGet());
            if (i == 60) assertEquals(429, s.getStatus());
        }
        assertEquals(60, chamadas.get());
    }

    @Test void consultasNaoConsomemCotaDeOperacoesPadrao() throws Exception {
        var filtro = new LimiteConvitesFilter(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));
        var chamadas = new AtomicInteger();
        for (int i = 0; i < 60; i++) {
            var p = new MockHttpServletRequest("POST", "/publico/excursoes/inscricoes");
            p.setRemoteAddr("198.51.100.10");
            var s1 = new MockHttpServletResponse();
            filtro.doFilter(p, s1, (a, b) -> chamadas.incrementAndGet());
            assertEquals(200, s1.getStatus());

            var g = new MockHttpServletRequest("GET", "/publico/excursoes/operacao/1/localizacao");
            g.setRemoteAddr("198.51.100.10");
            var s2 = new MockHttpServletResponse();
            filtro.doFilter(g, s2, (a, b) -> chamadas.incrementAndGet());
            assertEquals(200, s2.getStatus());
        }
        var p = new MockHttpServletRequest("POST", "/publico/excursoes/inscricoes");
        p.setRemoteAddr("198.51.100.10");
        var s = new MockHttpServletResponse();
        filtro.doFilter(p, s, (a, b) -> chamadas.incrementAndGet());
        assertEquals(429, s.getStatus());
        assertEquals(120, chamadas.get());
    }

    @Test void proxyConfiavelComIpInvalidoFalhaNaConfiguracao() {
        assertThrows(IllegalArgumentException.class, () -> new LimiteConvitesFilter(
                Clock.systemUTC(), java.util.Set.of("proxy.exemplo.test")));
        assertThrows(IllegalArgumentException.class, () -> new LimiteConvitesFilter(
                Clock.systemUTC(), java.util.Set.of("127.0.0.01")));
    }

    @Test void limiteGlobalNaoPodeSerContornadoTrocandoIp() throws Exception {
        var filtro=new LimiteConvitesFilter(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC));var chamadas=new AtomicInteger();
        for(int i=0;i<601;i++){
            var r=new MockHttpServletRequest("GET","/publico/excursoes/convite");r.setRemoteAddr("ip-"+i);var s=new MockHttpServletResponse();
            filtro.doFilter(r,s,(a,b)->chamadas.incrementAndGet());if(i==600)assertEquals(429,s.getStatus());
        }
        assertEquals(600,chamadas.get());
    }
}
