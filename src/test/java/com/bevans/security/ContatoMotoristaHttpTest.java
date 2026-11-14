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
    @Test void cadastroResponsavelNaoPermiteEscalarAdminOuAlterarIdentidade()throws Exception{
        var auth=mock(com.google.firebase.auth.FirebaseAuth.class);var token=mock(com.google.firebase.auth.FirebaseToken.class);when(token.getUid()).thenReturn("responsavel");when(token.getEmail()).thenReturn("r@test.invalid");when(auth.verifyIdToken("valido",true)).thenReturn(token);when(usuarioService.cadastrarUsuario(any())).thenAnswer(i->i.getArgument(0));
        try(var scope=mockStatic(com.google.firebase.auth.FirebaseAuth.class)){scope.when(com.google.firebase.auth.FirebaseAuth::getInstance).thenReturn(auth);
            mvc.perform(post("/usuarios/cadastrar").header("Authorization","Bearer valido").contentType("application/json").content("{\"nome\":\"R\",\"telefone\":\"5581999998888\",\"email\":\"r@test.invalid\",\"firebaseUid\":\"responsavel\",\"tipo\":\"RESPONSAVEL\",\"id\":999}")) .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("RESPONSAVEL"));
            mvc.perform(post("/usuarios/cadastrar").header("Authorization","Bearer valido").contentType("application/json").content("{\"email\":\"r@test.invalid\",\"firebaseUid\":\"responsavel\",\"tipo\":\"ADMIN\"}")) .andExpect(status().isBadRequest());
            mvc.perform(post("/usuarios/cadastrar").header("Authorization","Bearer valido").contentType("application/json").content("{\"email\":\"r@test.invalid\",\"firebaseUid\":\"terceiro\",\"tipo\":\"RESPONSAVEL\"}")) .andExpect(status().isForbidden());
        }verify(usuarioService).cadastrarUsuario(argThat(u->u.getId()==null&&u.getTipo().equals("RESPONSAVEL")&&u.getFirebaseUid().equals("responsavel")));
    }
    @Test void exclusaoPropriaRetornaPendenciaSemPrometerRemocaoRemota()throws Exception{
        identidade("proprio",usuario(1,"PASSAGEIRO"));try(var scope=firebase("valido","proprio")){
            mvc.perform(delete("/usuarios/1").header("Authorization","Bearer valido")).andExpect(status().isAccepted()).andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("pendente")));
            mvc.perform(delete("/usuarios/2").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }verify(usuarioService).excluirUsuario(1L);verify(usuarioService,never()).excluirUsuario(2L);
    }
}
