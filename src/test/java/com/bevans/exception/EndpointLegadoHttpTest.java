package com.bevans.exception;

import com.bevans.controller.PresencaController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EndpointLegadoHttpTest {

    @Test
    void endpointLegadoRetorna410ComJsonPadronizado() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PresencaController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/presenca/marcar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isGone())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(410))
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.erro").isString())
            .andExpect(jsonPath("$.caminho").value("/presenca/marcar"));
    }
}
