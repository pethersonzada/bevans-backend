package com.bevans.controller;
import com.bevans.dto.OperacaoDTO;
import com.bevans.model.TipoOperacao;
import com.bevans.security.AccessPolicy;
import com.bevans.service.OperacaoService;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/operacoes")
public class OperacaoController {
    private final OperacaoService service;private final AccessPolicy access;
    public OperacaoController(OperacaoService s,AccessPolicy a){service=s;access=a;}
    @GetMapping("/{tipo}/{ref}") public ResponseEntity<OperacaoDTO> painel(@PathVariable TipoOperacao tipo,@PathVariable Long ref){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.painel(tipo,ref,access.uid()));}
    @PostMapping("/{tipo}/{ref}/preparacao") public void preparar(@PathVariable TipoOperacao tipo,@PathVariable Long ref){service.prepararLegada(tipo,ref,access.uid());}
    @PutMapping("/{tipo}/{ref}/passageiros/{registro}/estado") public OperacaoDTO.Registro alterar(@PathVariable TipoOperacao tipo,@PathVariable Long ref,@PathVariable Long registro,@RequestBody OperacaoDTO.Alteracao d){return service.alterarDono(tipo,ref,registro,access.uid(),d);}
    @GetMapping("/{tipo}/{ref}/passageiros/{registro}/historico") public List<OperacaoDTO.Evento> historico(@PathVariable TipoOperacao tipo,@PathVariable Long ref,@PathVariable Long registro){return service.historico(tipo,ref,registro,access.uid());}
    @GetMapping("/VIAGEM/{ref}/minha") public OperacaoDTO.Registro minha(@PathVariable Long ref){return service.minha(ref,access.uid());}
    @PutMapping("/VIAGEM/{ref}/minha/estado") public OperacaoDTO.Registro alterarMinha(@PathVariable Long ref,@RequestBody OperacaoDTO.Alteracao d){return service.alterarMinha(ref,access.uid(),d);}
    @GetMapping("/VIAGEM/{ref}/minha/historico") public List<OperacaoDTO.Evento> historicoMeu(@PathVariable Long ref){return service.historicoMeu(ref,access.uid());}
}
