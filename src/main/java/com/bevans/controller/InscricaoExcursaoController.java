package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.InscricaoExcursaoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/excursoes/{id}/inscricoes") @PreAuthorize("@access.isMotorista()")
public class InscricaoExcursaoController {
    private final InscricaoExcursaoService service;private final AccessPolicy access;
    public InscricaoExcursaoController(InscricaoExcursaoService s,AccessPolicy a){service=s;access=a;}
    @GetMapping public PaginaDTO<InscricaoExcursaoDTO> listar(@PathVariable Long id,@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return service.listar(id,access.uid(),pagina,tamanho);}
    @PutMapping("/{inscricaoId}/confirmacao") public InscricaoExcursaoDTO confirmar(@PathVariable Long id,@PathVariable Long inscricaoId){return service.confirmar(id,inscricaoId,access.uid());}
    @DeleteMapping("/{inscricaoId}") public InscricaoExcursaoDTO cancelar(@PathVariable Long id,@PathVariable Long inscricaoId){return service.cancelarPeloDono(id,inscricaoId,access.uid());}
    @GetMapping("/vagas") public VagasExcursaoDTO vagas(@PathVariable Long id){return service.vagas(id,access.uid());}
}
