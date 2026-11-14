package com.bevans.controller;
import com.bevans.dto.NotificacaoDTO.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.DispositivoNotificacaoService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/notificacoes")
public class DispositivoNotificacaoController {
    private final DispositivoNotificacaoService service;private final AccessPolicy access;
    public DispositivoNotificacaoController(DispositivoNotificacaoService s,AccessPolicy a){service=s;access=a;}
    @GetMapping("/preferencias") public Preferencias preferencias(){return service.preferencias(access.uid());}
    @PutMapping("/preferencias") public Preferencias preferencias(@RequestBody Preferencias p){return service.preferencias(access.uid(),p);}
    @PostMapping("/dispositivos") public DispositivoInfo registrar(@RequestBody Dispositivo d){return service.registrar(access.uid(),d);}
    @DeleteMapping("/dispositivos/{instalacao}") public void revogar(@PathVariable String instalacao){service.revogar(access.uid(),instalacao);}
}
