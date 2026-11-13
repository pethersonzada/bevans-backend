package com.bevans.security;

import com.bevans.controller.TurmaController;
import com.bevans.dto.VagasTurmaDTO;
import com.bevans.exception.CapacidadeException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=TurmaController.class)
@Import({SecurityConfig.class, AccessPolicy.class})
class TurmaCapacidadeHttpTest extends BaseAutorizacaoHttpTest {
    @Test void painelDeVagasSomenteDoMotoristaDono() throws Exception {
        var dono=usuario(1,"MOTORISTA"); identidade("dono",dono); turma(7,dono);
        when(turmaService.consultarVagas(7L)).thenReturn(new VagasTurmaDTO(12,9,3L,true));
        try(var auth=firebase("token","dono")) {
            mvc.perform(get("/turmas/7/vagas").header("Authorization","Bearer token"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.ocupados").value(9)).andExpect(jsonPath("$.disponiveis").value(3));
        }
    }
    @Test void terceiroNaoConsultaNemEditaCapacidade() throws Exception {
        identidade("outro",usuario(2,"MOTORISTA")); turma(7,usuario(1,"MOTORISTA"));
        try(var auth=firebase("token","outro")) {
            mvc.perform(get("/turmas/7/vagas").header("Authorization","Bearer token")).andExpect(status().isForbidden());
            mvc.perform(put("/turmas/7").header("Authorization","Bearer token").contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":15}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(turmaService);
    }
    @Test void capacidadeFracionariaNaoETruncada() throws Exception {
        var dono=usuario(1,"MOTORISTA"); identidade("dono",dono); turma(7,dono);
        try(var auth=firebase("token","dono")) {
            mvc.perform(put("/turmas/7").header("Authorization","Bearer token").contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":1.5}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(turmaService);
    }
    @Test void lotacaoRetornaConflitoComMensagemDeDominio() throws Exception {
        var dono=usuario(1,"MOTORISTA"); identidade("dono",dono); turma(7,dono);
        when(turmaService.atualizarTurma(eq(7L),any())).thenThrow(new CapacidadeException("Capacidade abaixo de aprovados."));
        try(var auth=firebase("token","dono")) {
            mvc.perform(put("/turmas/7").header("Authorization","Bearer token").contentType(MediaType.APPLICATION_JSON).content("{\"capacidade\":1}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.erro").value("Capacidade abaixo de aprovados."));
        }
    }
}
