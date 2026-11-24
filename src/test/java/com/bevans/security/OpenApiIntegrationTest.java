package com.bevans.security;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.config.import=","app.firebase.enabled=false","spring.datasource.url=jdbc:h2:mem:openapi;DB_CLOSE_DELAY=-1",
 "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop","spring.sql.init.mode=never"})
@AutoConfigureMockMvc @ActiveProfiles("dev")
class OpenApiIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;
    @Test void documentoGeradoRefleteRotasReaisSchemasSegurancaECredenciais()throws Exception{
        var resposta=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();var api=json.readTree(resposta);var paths=api.path("paths");
        for(String caminho:new String[]{"/auth/login","/usuarios/cadastrar","/turmas","/enderecos/{id}","/rota/iniciar","/rotas-publicas","/excursoes","/operacoes/{tipo}/{ref}","/notificacoes","/motorista/contato","/responsaveis/vinculos","/viagens/{id}/localizacao/eventos"})assertTrue(paths.has(caminho),caminho);
        assertEquals("bearer",api.at("/components/securitySchemes/firebaseAuth/scheme").asText());assertEquals("X-Inscricao",api.at("/components/securitySchemes/Inscricao/name").asText());
        assertTrue(paths.path("/publico/excursoes/inscricoes").path("post").path("security").get(0).has("Convite"));assertTrue(paths.path("/publico/excursoes/inscricoes").path("post").path("security").get(0).has("Inscricao"));
        assertFalse(paths.path("/publico/excursoes/operacao/{id}/localizacao").path("get").path("security").toString().contains("Convite"));
        assertTrue(paths.path("/excursoes/{id}/convites").path("post").path("responses").has("201"));assertTrue(paths.path("/rotas-publicas/{id}/favorito").path("put").path("responses").has("204"));
        assertTrue(paths.path("/usuarios/{id}").path("delete").path("responses").has("202"));
        assertTrue(paths.path("/viagens/{id}/localizacao/eventos").path("get").path("responses").path("200").path("content").has("text/event-stream"));
        assertFalse(api.at("/components/schemas/Usuario/properties").has("telefone"));assertTrue(api.at("/components/schemas/PerfilUsuarioDTO/properties").has("telefone"));assertTrue(api.at("/components/schemas/ErroAPI/properties").has("erro"));
        assertTrue(paths.path("/notificacoes").path("get").path("parameters").toString().contains("pagina"));
        assertTrue(api.at("/components/schemas/ErroAPI/properties").has("mensagem"));
        assertTrue(api.at("/components/schemas/CadastroUsuario/properties").has("firebaseUid"));assertTrue(api.at("/components/schemas/CadastroUsuario/properties").has("codigoAcesso"));
        assertTrue(api.at("/components/schemas/ParadaPrivadaEntrada/properties").has("pontoConfirmado"));assertFalse(api.at("/components/schemas/ParadaPrivadaSaida/properties").has("pontoConfirmado"));assertTrue(api.at("/components/schemas/ParadaPrivadaSaida/properties").has("ordem"));
        assertTrue(api.at("/components/schemas/EventoOperacional/properties").has("anterior"));assertTrue(api.at("/components/schemas/EventoResponsabilidade/properties").has("termoVersao"));verificarReferencias(api,api);
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());mvc.perform(get("/notificacoes")).andExpect(status().isUnauthorized());
    }
    private void verificarReferencias(JsonNode documento,JsonNode no){if(no.has("$ref")&&no.path("$ref").asText().startsWith("#/"))assertFalse(documento.at(no.path("$ref").asText().substring(1)).isMissingNode(),no.path("$ref").asText());for(JsonNode filho:no)verificarReferencias(documento,filho);}
}
