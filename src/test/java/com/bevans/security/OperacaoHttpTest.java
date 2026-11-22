package com.bevans.security;
import com.bevans.controller.*;
import com.bevans.service.OperacaoService;
import com.bevans.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers={OperacaoController.class,OperacaoExcursaoPublicoController.class,ExecucoesInscricaoPublicoController.class}) @Import({SecurityConfig.class,AccessPolicy.class})
class OperacaoHttpTest extends BaseAutorizacaoHttpTest {
    @MockBean OperacaoService service;
    @Test void roteiroCompletoExigeFirebaseUsaReferenciaENaoPermiteCache() throws Exception {
        mvc.perform(get("/operacoes/VIAGEM/5/roteiro")).andExpect(status().isUnauthorized());
        identidade("motorista",usuario(1,"MOTORISTA"));
        when(service.roteiroViagem(5L,"motorista")).thenReturn(java.util.List.of(new com.bevans.dto.PontoColetaPrivadaDTO("ORIGEM",null,"Origem",-8,-35,java.util.List.of()),new com.bevans.dto.PontoColetaPrivadaDTO("DESTINO",null,"Destino",-8.3,-35.3,java.util.List.of())));
        try(var ignored=firebase("valido","motorista")){
            mvc.perform(get("/operacoes/VIAGEM/5/roteiro").header("Authorization","Bearer valido"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$[0].tipo").value("ORIGEM")).andExpect(jsonPath("$[1].tipo").value("DESTINO"));
        }verify(service).roteiroViagem(5L,"motorista");
    }
    @Test void rotaAutenticadaNaoAceitaTokenDeConviteEAtorVemDoFirebase() throws Exception {
        mvc.perform(get("/operacoes/VIAGEM/1").header("X-Convite","token")).andExpect(status().isUnauthorized());
        try(var ignored=firebase("valido","aluno")){
            mvc.perform(put("/operacoes/VIAGEM/1/minha/estado").header("Authorization","Bearer valido").contentType("application/json").content("{\"estado\":\"EMBARCADO\",\"versao\":0,\"alunoId\":99}"))
                    .andExpect(status().isOk());
        }verify(service).alterarMinha(eq(1L),eq("aluno"),argThat(d->d.estado()==EstadoOperacional.EMBARCADO));
    }
    @Test void webUsaSoCredencialIndividualEIdDaExecucao() throws Exception {
        mvc.perform(get("/publico/excursoes/operacao/5/minha").header("X-Inscricao","credencial")).andExpect(status().isOk());
        verify(service).minhaWeb(5L,"credencial");mvc.perform(get("/publico/excursoes/operacao/5/passageiros")).andExpect(status().isUnauthorized());
    }
    @Test void inscricaoSoConsultaSuasExecucoesPorCredencialSemCache() throws Exception {
        when(service.execucoesWeb("credencial")).thenReturn(java.util.List.of(
                new com.bevans.dto.ExecucaoInscricaoDTO(7L,"IDA",EstadoExecucaoExcursao.EM_ANDAMENTO,
                        java.time.Instant.parse("2026-11-22T10:00:00Z"))));
        mvc.perform(get("/publico/excursoes/inscricao/execucoes").header("X-Inscricao","credencial"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Referrer-Policy","no-referrer"))
                .andExpect(header().string("X-Robots-Tag","noindex, nofollow, noarchive"))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].sentido").value("IDA"))
                .andExpect(jsonPath("$[0].estado").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$[0].latitude").doesNotExist())
                .andExpect(jsonPath("$[0].inscricaoId").doesNotExist());
        verify(service).execucoesWeb("credencial");
    }
}
