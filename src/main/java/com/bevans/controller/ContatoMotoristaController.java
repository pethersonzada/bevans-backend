package com.bevans.controller;
import com.bevans.dto.ContatoMotoristaDTO;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ContatoMotoristaService;
import org.springframework.web.bind.annotation.*;
@RestController
public class ContatoMotoristaController {
    private final ContatoMotoristaService service;private final AccessPolicy access;
    public ContatoMotoristaController(ContatoMotoristaService s,AccessPolicy a){service=s;access=a;}
    @GetMapping("/motorista/contato") public ContatoMotoristaDTO.Configuracao minha(){return service.minha(access.uid());}
    @PutMapping("/motorista/contato") public ContatoMotoristaDTO.Configuracao configurar(@RequestBody ContatoMotoristaDTO.Configurar d){return service.configurar(access.uid(),d);}
    @GetMapping("/turmas/{id}/contato-motorista") public ContatoMotoristaDTO turma(@PathVariable Long id){return service.turma(id,access.uid());}
    @GetMapping("/rotas-publicas/{id}/contato-motorista") public ContatoMotoristaDTO rota(@PathVariable Long id){return service.rota(id,access.uid());}
    @GetMapping("/publico/excursoes/inscricao/contato-motorista") public ContatoMotoristaDTO inscricao(@RequestHeader(value="X-Inscricao",required=false)String token){return service.inscricao(token);}
}
