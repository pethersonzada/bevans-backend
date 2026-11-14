package com.bevans.security;
import com.bevans.controller.*;
import com.bevans.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={ContatoMotoristaController.class,UsuarioController.class}) @Import({SecurityConfig.class,AccessPolicy.class})
class ContatoMotoristaHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean ContatoMotoristaService service;@MockBean UsuarioService usuarioService;
    @Test void contatoExigeFirebaseEAtorNaoVemDoPayload()throws Exception{
        mvc.perform(get("/turmas/1/contato-motorista")).andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","motorista")){
            mvc.perform(put("/motorista/contato").header("Authorization","Bearer valido").contentType("application/json").content("{\"telefone\":\"5581999998888\",\"habilitado\":true,\"aceiteDivulgacao\":true,\"versao\":0,\"motoristaId\":999}")) .andExpect(status().isOk());
        }verify(service).configurar(eq("motorista"),argThat(d->d.aceiteDivulgacao()));
    }
    @Test void perfilCompletoContinuaDisponivelSomenteAoProprioUid()throws Exception{
        var u=usuario(1,"PASSAGEIRO");u.setNome("Próprio");u.setTelefone("5581999998888");u.setEmail("proprio@test.invalid");u.setFirebaseUid("proprio");identidade("proprio",u);
        try(var ignored=firebase("valido","proprio")){
            mvc.perform(get("/usuarios/por-uid/proprio").header("Authorization","Bearer valido")).andExpect(status().isOk()).andExpect(jsonPath("$.telefone").value("5581999998888")).andExpect(jsonPath("$.email").value("proprio@test.invalid"));
            mvc.perform(get("/usuarios/por-uid/terceiro").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }
    }
}
