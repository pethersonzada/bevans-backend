package com.bevans.security;
import com.bevans.controller.AuthController;
import com.google.firebase.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AuthController.class) @Import({SecurityConfig.class,AccessPolicy.class,com.bevans.service.AutenticacaoPerfilService.class})
class IdentidadeLegadaHttpTest extends BaseAutorizacaoHttpTest {
    @org.springframework.boot.test.mock.mockito.MockBean jakarta.persistence.EntityManager em;
    @Test void emailNaoVerificadoNaoApropriaPerfilLegado()throws Exception{
        var legado=usuario(10,"MOTORISTA");legado.setEmail("legado@test.invalid");when(usuarios.findByEmail(legado.getEmail())).thenReturn(Optional.of(legado));
        var auth=mock(FirebaseAuth.class);var token=mock(FirebaseToken.class);when(token.getUid()).thenReturn("outra-identidade");when(token.getEmail()).thenReturn(legado.getEmail());when(token.getClaims()).thenReturn(Map.of("email_verified",false));when(auth.verifyIdToken("teste",true)).thenReturn(token);
        try(var scope=mockStatic(FirebaseAuth.class)){scope.when(FirebaseAuth::getInstance).thenReturn(auth);mvc.perform(post("/auth/login").header("Authorization","Bearer teste").contentType("application/json").content("{\"email\":\"legado@test.invalid\"}")) .andExpect(status().isForbidden());}
        verify(usuarios,never()).save(any());
    }
    @Test void tokenRevogadoRecebe401AntesDeConsultarPerfil()throws Exception{
        var auth=mock(FirebaseAuth.class);when(auth.verifyIdToken("revogado",true)).thenThrow(new IllegalArgumentException("token de teste revogado"));
        try(var scope=mockStatic(FirebaseAuth.class)){scope.when(FirebaseAuth::getInstance).thenReturn(auth);mvc.perform(post("/auth/login").header("Authorization","Bearer revogado").contentType("application/json").content("{\"email\":\"pessoa@test.invalid\"}")) .andExpect(status().isUnauthorized());}
        verifyNoInteractions(usuarios);verify(auth).verifyIdToken("revogado",true);
    }
    @Test void indisponibilidadeFirebaseNaoEConfundidaComTokenRevogado()throws Exception{
        var auth=mock(FirebaseAuth.class);var erro=mock(FirebaseAuthException.class);when(erro.getErrorCode()).thenReturn(com.google.firebase.ErrorCode.UNAVAILABLE);when(auth.verifyIdToken("indisponivel",true)).thenThrow(erro);
        try(var scope=mockStatic(FirebaseAuth.class)){scope.when(FirebaseAuth::getInstance).thenReturn(auth);mvc.perform(post("/auth/login").header("Authorization","Bearer indisponivel").contentType("application/json").content("{\"email\":\"pessoa@test.invalid\"}")) .andExpect(status().isServiceUnavailable());}
        verifyNoInteractions(usuarios);
    }
}
