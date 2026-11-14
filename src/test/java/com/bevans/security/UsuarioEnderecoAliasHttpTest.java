package com.bevans.security;
import com.bevans.controller.UsuarioController;
import com.bevans.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(UsuarioController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class UsuarioEnderecoAliasHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean UsuarioService service;
    @Test void aliasLegadoNaoSalvaCoordenadasInvalidasOuIncompletas()throws Exception{
        var p=usuario(1,"PASSAGEIRO");identidade("proprio",p);when(usuarios.findById(1L)).thenReturn(Optional.of(p));
        try(var scope=firebase("valido","proprio")){
            mvc.perform(put("/usuarios/salvar-endereco").header("Authorization","Bearer valido").contentType("application/json").content("{\"idUsuario\":1,\"latitude\":91,\"longitude\":-35}")) .andExpect(status().isBadRequest());
            mvc.perform(put("/usuarios/salvar-endereco").header("Authorization","Bearer valido").contentType("application/json").content("{\"idUsuario\":1,\"latitude\":-8}")) .andExpect(status().isBadRequest());
        }verify(usuarios,never()).save(any());
    }
}
