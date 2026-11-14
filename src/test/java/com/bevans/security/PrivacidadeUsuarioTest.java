package com.bevans.security;
import com.bevans.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PrivacidadeUsuarioTest {
    @Test void perfilAninhadoNaoExibeTelefoneEmailUidOuEnderecoPrivados()throws Exception{
        var u=new Usuario();u.setId(1L);u.setTipo("MOTORISTA");u.setNome("Nome público");u.setTelefone("5581999998888");u.setEmail("privado@example.invalid");u.setFirebaseUid("uid-privado");u.setEnderecoCompleto("Residência privada");u.setLatitude(-8.2);u.setLongitude(-35.1);
        var t=new Turma();t.setMotorista(u);String json=new ObjectMapper().writeValueAsString(t);
        assertFalse(json.contains("5581999998888"));assertFalse(json.contains("privado@example.invalid"));assertFalse(json.contains("uid-privado"));assertFalse(json.contains("Residência privada"));assertFalse(json.contains("-8.2"));assertTrue(json.contains("Nome público"));
    }
}
