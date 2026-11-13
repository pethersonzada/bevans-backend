package com.bevans.service;

import com.bevans.model.Endereco;
import com.bevans.model.Usuario;
import com.bevans.repository.EnderecoRepository;
import com.bevans.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnderecoServiceTest {
    @Mock EnderecoRepository enderecos;
    @Mock UsuarioRepository usuarios;
    EnderecoService service;

    @BeforeEach void setup() { service = new EnderecoService(enderecos, usuarios); }

    @ParameterizedTest
    @CsvSource(value = {"null,-35", "-8,null", "NaN,-35", "-8,Infinity", "91,-35", "-8,181", "0,0"}, nullValues = "null")
    void criacaoRejeitaCoordenadasInvalidasSemEscrever(Double lat, Double lon) {
        Endereco e = new Endereco();
        e.setLatitude(lat);
        e.setLongitude(lon);
        assertThrows(IllegalArgumentException.class, () -> service.cadastrarEndereco(1L, e));
        verifyNoInteractions(enderecos, usuarios);
    }

    @Test void criacaoNaoPermiteSobrescreverIdNemProprietarioDoPayload() {
        Usuario dono = new Usuario();
        when(usuarios.findById(1L)).thenReturn(Optional.of(dono));
        Endereco e = new Endereco();
        e.setId(99L);
        e.setUsuario(new Usuario());
        e.setLatitude(-8.0);
        e.setLongitude(-35.0);
        when(enderecos.save(e)).thenReturn(e);
        assertSame(e, service.cadastrarEndereco(1L, e));
        assertNull(e.getId());
        assertSame(dono, e.getUsuario());
        assertEquals(-8.0, dono.getLatitude());
    }

    @Test void edicaoPersisteComplementoSemExigirNumeroOuAlterarCoordenadas() {
        Endereco salvo = new Endereco();
        salvo.setLatitude(-8.0);
        salvo.setLongitude(-35.0);
        when(enderecos.findById(3L)).thenReturn(Optional.of(salvo));
        Endereco patch = new Endereco();
        patch.setComplemento("Portão lateral");
        service.atualizarEndereco(3L, patch);
        assertEquals("Portão lateral", salvo.getComplemento());
        assertNull(salvo.getNumero());
        assertEquals(-8.0, salvo.getLatitude());
        verify(enderecos).save(salvo);
    }
}
