package com.bevans.service;

import com.bevans.exception.ConflitoException;
import com.bevans.model.Usuario;
import com.bevans.repository.PresencaRepository;
import com.bevans.repository.UsuarioRepository;
import com.bevans.repository.ViagemRepository;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsuarioServiceConflitosTest {

    private final UsuarioRepository usuarios =
            mock(UsuarioRepository.class);

    private final UsuarioService service = new UsuarioService(
            usuarios,
            mock(PresencaRepository.class),
            mock(ViagemRepository.class)
    );

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setEmail("teste@example.com");
        u.setTelefone("81999999999");
        return u;
    }

    @Test
    void emailDuplicadoRetornaConflito() {
        Usuario u = usuario();

        when(usuarios.findByEmail(u.getEmail()))
                .thenReturn(Optional.of(new Usuario()));

        assertThrows(
                ConflitoException.class,
                () -> service.cadastrarUsuario(u)
        );

        verify(usuarios, never()).save(any());
    }

    @Test
    void telefoneDuplicadoRetornaConflito() {
        Usuario u = usuario();

        when(usuarios.findByTelefone(u.getTelefone()))
                .thenReturn(Optional.of(new Usuario()));

        assertThrows(
                ConflitoException.class,
                () -> service.cadastrarUsuario(u)
        );

        verify(usuarios, never()).save(any());
    }

    @Test
    void idNuloRetornaErroDeValidacao() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.buscarPorId(null)
        );

        verifyNoInteractions(usuarios);
    }
}
