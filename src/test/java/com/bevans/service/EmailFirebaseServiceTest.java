package com.bevans.service;

import com.bevans.exception.ConflitoException;
import com.bevans.model.Usuario;
import com.bevans.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailFirebaseServiceTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final UsuarioService service = new UsuarioService(usuarios,
            mock(PresencaRepository.class), mock(ViagemRepository.class));

    private Usuario usuario() {
        var u = new Usuario();
        u.setId(7L);
        u.setEmail("antigo@example.test");
        return u;
    }

    @Test void sincronizacaoRepetidaEhIdempotente() {
        var u = usuario();
        when(usuarios.lockById(7L)).thenReturn(Optional.of(u));
        assertSame(u, service.atualizarEmail(7L, " ANTIGO@example.test "));
        verify(usuarios, never()).save(any());
        verify(usuarios, never()).saveAndFlush(any());
        verify(usuarios, never()).findByEmail(anyString());
    }

    @Test void alteracaoGravaEmailNormalizadoSobLock() {
        var u = usuario();
        when(usuarios.lockById(7L)).thenReturn(Optional.of(u));
        when(usuarios.saveAndFlush(u)).thenReturn(u);
        assertSame(u, service.atualizarEmail(7L, " novo@example.test "));
        assertEquals("novo@example.test", u.getEmail());
        verify(usuarios).lockById(7L);
        verify(usuarios).findByEmail("novo@example.test");
        verify(usuarios).saveAndFlush(u);
    }

    @Test void emailDeOutroUsuarioEhConflito() {
        var u = usuario();
        when(usuarios.lockById(7L)).thenReturn(Optional.of(u));
        when(usuarios.findByEmail("ocupado@example.test"))
            .thenReturn(Optional.of(new Usuario()));
        assertThrows(ConflitoException.class,
                () -> service.atualizarEmail(7L, "ocupado@example.test"));
        assertEquals("antigo@example.test", u.getEmail());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test void disputaDeUnicidadeNoBancoRetorna409DeDominio() {
        var u = usuario();
        when(usuarios.lockById(7L)).thenReturn(Optional.of(u));
        when(usuarios.saveAndFlush(u))
            .thenThrow(new DataIntegrityViolationException("unicidade"));
        assertThrows(ConflitoException.class,
                () -> service.atualizarEmail(7L, "concorrente@example.test"));
    }
}
