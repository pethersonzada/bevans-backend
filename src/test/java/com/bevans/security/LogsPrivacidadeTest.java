package com.bevans.security;
import com.bevans.exception.GlobalExceptionHandler;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.WebRequest;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class LogsPrivacidadeTest {
    @Test void erroInesperadoNaoRegistraMensagemOuStackComDadosPrivados(){
        var logger=(Logger)LoggerFactory.getLogger(GlobalExceptionHandler.class);var captura=new ListAppender<ILoggingEvent>();captura.start();logger.addAppender(captura);
        try{var request=mock(WebRequest.class);when(request.getDescription(false)).thenReturn("uri=/teste");var resposta=new GlobalExceptionHandler().erroInterno(new IllegalStateException("segredo-sintetico-na-excecao"),request);
            assertEquals(500,resposta.getStatusCode().value());assertNotNull(resposta.getHeaders().getFirst("X-Correlation-Id"));assertFalse(resposta.getBody().toString().contains("segredo-sintetico"));assertFalse(captura.list.isEmpty());
            assertTrue(captura.list.stream().allMatch(e->e.getThrowableProxy()==null&&!e.getFormattedMessage().contains("segredo-sintetico")));
        }finally{logger.detachAppender(captura);captura.stop();}
    }
}
