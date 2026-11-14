package com.bevans.security;
import com.bevans.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(HealthController.class) @Import({SecurityConfig.class,AccessPolicy.class}) @ActiveProfiles({"dev","prod"})
class OpenApiProducaoHttpTest extends BaseAutorizacaoHttpTest {
    @Test void perfilProdPrevaleceSobreDevENaoPublicaDocumentacao()throws Exception{
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
}
