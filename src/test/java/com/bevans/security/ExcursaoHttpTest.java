package com.bevans.security;
import com.bevans.controller.ExcursaoController;
import com.bevans.service.ExcursaoService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=ExcursaoController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class ExcursaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean ExcursaoService service;
    @Test void exigeTokenFirebase() throws Exception {mvc.perform(get("/excursoes")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @Test void passageiroNaoPodeGerenciarExcursao() throws Exception {
        identidade("passageiro",usuario(2,"PASSAGEIRO"));try(var ignored=firebase("valido","passageiro")){
            mvc.perform(put("/excursoes/1/cancelamento").header("Authorization","Bearer valido")).andExpect(status().isForbidden());
        }verifyNoInteractions(service);
    }
    @Test void identidadeDoOrganizadorVemDoToken() throws Exception {
        identidade("motorista",usuario(1,"MOTORISTA"));try(var ignored=firebase("valido","motorista")){
            mvc.perform(get("/excursoes/1").param("organizadorId","99").header("Authorization","Bearer valido")).andExpect(status().isOk());
        }verify(service).consultar(1L,"motorista");
    }
}
