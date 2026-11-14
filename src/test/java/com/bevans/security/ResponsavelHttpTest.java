package com.bevans.security;
import com.bevans.controller.ResponsavelController;
import com.bevans.service.ResponsavelService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ResponsavelController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class ResponsavelHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean ResponsavelService service;
    @Test void exigeFirebaseEIgnoraAtorEProvaForjadosNoBody()throws Exception{
        mvc.perform(post("/responsaveis/convites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/responsaveis/vinculos/1/localizacao?viagemId=2").header("X-Inscricao","token")).andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","passageiro")){
            mvc.perform(put("/responsaveis/vinculos/5/aprovacao").header("Authorization","Bearer valido").contentType("application/json").content("{\"versao\":0,\"aceite\":true,\"termoVersao\":\"1\",\"comprovada\":true,\"passageiroId\":999}")) .andExpect(status().isOk());
        }verify(service).aprovar(eq(5L),eq("passageiro"),argThat(d->d.versao()==0&&d.aceite()));
    }
}
