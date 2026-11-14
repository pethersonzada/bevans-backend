package com.bevans.controller;
import com.bevans.dto.OperacaoDTO;
import com.bevans.service.OperacaoService;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/publico/excursoes/operacao/{id}/minha")
public class OperacaoExcursaoPublicoController {
    private final OperacaoService service;
    public OperacaoExcursaoPublicoController(OperacaoService s){service=s;}
    @GetMapping public ResponseEntity<OperacaoDTO.Registro> minha(@PathVariable Long id,@RequestHeader(name="X-Inscricao",required=false)String token){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.minhaWeb(id,token));}
    @PutMapping("/estado") public OperacaoDTO.Registro alterar(@PathVariable Long id,@RequestHeader(name="X-Inscricao",required=false)String token,@RequestBody OperacaoDTO.Alteracao d){return service.alterarWeb(id,token,d);}
    @GetMapping("/historico") public List<OperacaoDTO.Evento> historico(@PathVariable Long id,@RequestHeader(name="X-Inscricao",required=false)String token){return service.historicoWeb(id,token);}
}
