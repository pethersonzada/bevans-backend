package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.service.InscricaoExcursaoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/publico/excursoes")
public class InscricaoExcursaoPublicoController {
    private final InscricaoExcursaoService service;
    public InscricaoExcursaoPublicoController(InscricaoExcursaoService s){service=s;}
    private <T> ResponseEntity<T> privado(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(body);}
    @PostMapping("/credenciais") public ResponseEntity<CredencialInscricaoEmitidaDTO> credencial(@RequestHeader(name="X-Convite",required=false)String convite){return privado(service.emitirCredencial(convite));}
    @PostMapping("/inscricoes") public ResponseEntity<InscricaoExcursaoDTO> inscrever(@RequestHeader(name="X-Convite",required=false)String convite,@RequestHeader(name="X-Inscricao",required=false)String token,@RequestBody InscricaoExcursaoRequest d){return privado(service.solicitar(convite,token,d));}
    @GetMapping("/inscricao") public ResponseEntity<InscricaoExcursaoDTO> consultar(@RequestHeader(name="X-Inscricao",required=false)String token){return privado(service.consultar(token));}
    @DeleteMapping("/inscricao") public ResponseEntity<InscricaoExcursaoDTO> cancelar(@RequestHeader(name="X-Inscricao",required=false)String token){return privado(service.cancelar(token));}
}
