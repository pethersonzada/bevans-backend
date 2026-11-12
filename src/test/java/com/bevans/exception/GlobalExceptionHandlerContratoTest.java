package com.bevans.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerContratoTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private WebRequest request() {
        return new ServletWebRequest(
                new MockHttpServletRequest("GET", "/teste")
        );
    }

    private void verificar(ResponseEntity<Object> resposta,
                          int status, String mensagem) {
        assertEquals(status, resposta.getStatusCode().value());
        assertInstanceOf(Map.class, resposta.getBody());

        Map<?, ?> corpo = (Map<?, ?>) resposta.getBody();

        assertEquals(status, corpo.get("status"));
        assertEquals(mensagem, corpo.get("erro"));
        assertEquals("/teste", corpo.get("caminho"));
        assertNotNull(corpo.get("timestamp"));
        assertEquals(4, corpo.size());
    }

    @Test
    void dadosInvalidosRetornam400() {
        verificar(
                handler.argumentoInvalido(
                        new IllegalArgumentException("ID inválido"),
                        request()
                ),
                400, "ID inválido"
        );
    }

    @Test
    void recursoInexistenteRetorna404() {
        verificar(
                handler.recursoNaoEncontrado(
                        new RecursoNaoEncontradoException("Não encontrado"),
                        request()
                ),
                404, "Não encontrado"
        );
    }

    @Test
    void conflitoRetorna409() {
        verificar(
                handler.conflito(
                        new ConflitoException("Recurso duplicado"),
                        request()
                ),
                409, "Recurso duplicado"
        );
    }

    @Test
    void falhaInternaRetorna500SemExporDetalhes() {
        verificar(
                handler.erroInterno(
                        new IllegalStateException("Segredo interno"),
                        request()
                ),
                500, "Erro interno do servidor."
        );
    }
}
