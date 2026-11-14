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
    @Test void limiteGlobalNaoPodeSerContornadoTrocandoIp() throws Exception {
        var filtro=new LimiteConvitesFilter(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC));var chamadas=new AtomicInteger();
        for(int i=0;i<601;i++){
            var r=new MockHttpServletRequest("GET","/publico/excursoes/convite");r.setRemoteAddr("ip-"+i);var s=new MockHttpServletResponse();
            filtro.doFilter(r,s,(a,b)->chamadas.incrementAndGet());if(i==600)assertEquals(429,s.getStatus());
        }
        assertEquals(600,chamadas.get());
    }
}
