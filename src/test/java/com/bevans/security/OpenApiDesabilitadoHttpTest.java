package com.bevans.security;
import com.bevans.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(HealthController.class) @Import({SecurityConfig.class,AccessPolicy.class})
class OpenApiDesabilitadoHttpTest extends BaseAutorizacaoHttpTest {
    @Test void documentacaoDesabilitadaNaoAbreNemComFirebase()throws Exception{
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","usuario")){mvc.perform(get("/v3/api-docs").header("Authorization","Bearer valido")).andExpect(status().isForbidden());mvc.perform(get("/swagger-ui/index.html").header("Authorization","Bearer valido")).andExpect(status().isForbidden());}
    }
}
