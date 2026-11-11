package com.bevans.service;

import com.bevans.exception.GlobalExceptionHandler;
import com.bevans.exception.RecursoNaoEncontradoException;
import com.bevans.model.Endereco;
import com.bevans.repository.EnderecoRepository;
import com.bevans.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnderecoServiceNaoEncontradoTest {

    private final EnderecoRepository enderecos =
            mock(EnderecoRepository.class);

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final EnderecoService service =
            new EnderecoService(enderecos, usuarios);

    @Test
    void cadastroComUsuarioInexistente() {
        when(usuarios.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.cadastrarEndereco(999L, new Endereco())
        );

        verify(enderecos, never()).save(any());
    }

    @Test
    void edicaoDeEnderecoInexistente() {
        when(enderecos.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.atualizarEndereco(999L, new Endereco())
        );

        verify(enderecos, never()).save(any());
    }

    @Test
    void exclusaoDeEnderecoInexistente() {
        when(enderecos.existsById(999L)).thenReturn(false);

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.excluirEndereco(999L)
        );

        verify(enderecos, never()).deleteById(anyLong());
    }

    @Test
    void excecaoDeRecursoInexistenteRetorna404() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/enderecos/999");

        ResponseEntity<Object> resposta = handler.recursoNaoEncontrado(
                new RecursoNaoEncontradoException("Endereço não encontrado"),
                new ServletWebRequest(request)
        );

        assertEquals(404, resposta.getStatusCode().value());

        assertInstanceOf(Map.class, resposta.getBody());

        Map<?, ?> corpo = (Map<?, ?>) resposta.getBody();

        assertEquals(404, corpo.get("status"));
        assertEquals("Endereço não encontrado", corpo.get("erro"));
        assertEquals("/enderecos/999", corpo.get("caminho"));
        assertNotNull(corpo.get("timestamp"));
    }
}
