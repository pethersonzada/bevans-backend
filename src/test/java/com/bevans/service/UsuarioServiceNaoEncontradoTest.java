package com.bevans.service;

import com.bevans.exception.RecursoNaoEncontradoException;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.PresencaRepository;
import com.bevans.repository.ViagemRepository;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceNaoEncontradoTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final PresencaRepository presencas =
            mock(PresencaRepository.class);

    private final ViagemRepository viagens =
            mock(ViagemRepository.class);

    private final UsuarioService service =
            new UsuarioService(usuarios, presencas, viagens);

    @Test
    void buscarUsuarioInexistente() {
        when(usuarios.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.buscarPorId(999L)
        );
    }

    @Test
    void atualizarEmailDeUsuarioInexistente() {
        when(usuarios.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.atualizarEmail(
                        999L, "teste@example.com"
                )
        );

        verify(usuarios, never()).save(any());
    }

    @Test
    void excluirUsuarioInexistente() {
        when(usuarios.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.excluirUsuario(999L)
        );

        verify(usuarios, never()).deleteById(anyLong());
        verifyNoInteractions(presencas, viagens);
    }
}
