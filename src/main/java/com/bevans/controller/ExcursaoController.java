package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ExcursaoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/excursoes") @PreAuthorize("@access.isMotorista()")
public class ExcursaoController {
    private final ExcursaoService service;private final AccessPolicy access;
    public ExcursaoController(ExcursaoService service,AccessPolicy access){this.service=service;this.access=access;}
    @PostMapping public ExcursaoDTO criar(@RequestBody ExcursaoRequest d){return service.criar(access.uid(),d);}
    @GetMapping public PaginaDTO<ExcursaoDTO> minhas(@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return service.minhas(access.uid(),pagina,tamanho);}
    @GetMapping("/{id}") public ExcursaoDTO consultar(@PathVariable Long id){return service.consultar(id,access.uid());}
    @PutMapping("/{id}") public ExcursaoDTO atualizar(@PathVariable Long id,@RequestBody ExcursaoRequest d){return service.atualizar(id,access.uid(),d);}
    public record VersaoRequest(Long versao){}
    @PutMapping("/{id}/publicacao") public ExcursaoDTO publicar(@PathVariable Long id,@RequestBody VersaoRequest d){if(d==null)throw new IllegalArgumentException("Informe versão.");return service.publicar(id,access.uid(),d.versao());}
    @PutMapping("/{id}/cancelamento") public ExcursaoDTO cancelar(@PathVariable Long id){return service.cancelar(id,access.uid());}
    @PutMapping("/{id}/coleta") public ExcursaoDTO coleta(@PathVariable Long id,@RequestBody ColetaExcursaoRequest d){return service.configurarColeta(id,access.uid(),d);}
}
