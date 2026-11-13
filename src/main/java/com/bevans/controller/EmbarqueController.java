package com.bevans.controller;

import com.bevans.dto.*;
import com.bevans.model.StatusEmbarque;
import com.bevans.security.AccessPolicy;
import com.bevans.service.EmbarqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@Tag(name="Embarques em Rotas Públicas",description="Pedidos privados; um pin solicitado nunca vira parada pública automaticamente.")
public class EmbarqueController {
    private final EmbarqueService service;
    private final AccessPolicy access;
    public EmbarqueController(EmbarqueService service,AccessPolicy access){this.service=service;this.access=access;}

    @PostMapping("/rotas-publicas/{rotaId}/embarques")
    @PreAuthorize("@access.isPassageiro()")
    @Operation(summary="Pedir embarque em parada ou pin confirmado",description="FIXA exige paradaId. FLEXIVEL aceita paradaId OU nomePonto/latitude/longitude/pontoConfirmado=true. Data ISO e sentido IDA/VOLTA obrigatórios.")
    public EmbarqueDTO solicitar(@PathVariable Long rotaId,@RequestBody EmbarqueRequest dados){return service.solicitar(rotaId,access.usuario().getId(),dados);}

    @GetMapping("/embarques/minhas")
    @PreAuthorize("@access.isPassageiro()")
    public List<EmbarqueDTO> minhas(){return service.minhas(access.usuario().getId());}

    @GetMapping("/rotas-publicas/{rotaId}/embarques")
    @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    public List<EmbarqueDTO> daRota(@PathVariable Long rotaId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate data,@RequestParam String sentido){
        return service.daRota(rotaId,access.usuario().getId(),data,sentido);
    }

    public record AnaliseRequest(StatusEmbarque status) {}
    @PutMapping("/embarques/{id}/analise")
    @PreAuthorize("@access.isMotorista()")
    @Operation(summary="Aprovar ou rejeitar pedido",description="Service revalida o proprietário. Aprovação reutiliza AlunoTurma e o controle transacional de capacidade; não cria parada pública.")
    public EmbarqueDTO analisar(@PathVariable Long id,@RequestBody AnaliseRequest dados){
        if(dados==null)throw new IllegalArgumentException("Informe o status.");return service.analisar(id,access.usuario().getId(),dados.status());
    }

    @DeleteMapping("/embarques/{id}")
    @PreAuthorize("@access.isPassageiro()")
    public EmbarqueDTO cancelar(@PathVariable Long id){return service.cancelar(id,access.usuario().getId());}

    @PostMapping("/rotas-publicas/{rotaId}/embarques/planejamento")
    @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    @Operation(summary="Planejar execução incluindo somente embarques aprovados",description="Acesso exclusivo do dono. Coordenadas residenciais solicitadas não aparecem nos detalhes públicos nem em paradas públicas.")
    public List<PontoPublicoDTO> planejar(@PathVariable Long rotaId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam String sentido,@RequestParam(defaultValue="false") boolean otimizar){
        return service.planejamento(rotaId,access.usuario().getId(),data,sentido,otimizar);
    }
}
