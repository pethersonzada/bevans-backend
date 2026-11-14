package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController
public class PosicaoExcursaoController {
    private final PosicaoExcursaoService service;private final PosicaoStreamService streams;private final AccessPolicy access;
    public PosicaoExcursaoController(PosicaoExcursaoService s,PosicaoStreamService p,AccessPolicy a){service=s;streams=p;access=a;}
    @PutMapping("/execucoes-excursao/{id}/localizacao") public ResponseEntity<PosicaoExcursaoDTO> enviar(@PathVariable Long id,@RequestBody PosicaoRequest d){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.enviar(id,access.uid(),d));}
    @GetMapping("/execucoes-excursao/{id}/localizacao") public ResponseEntity<PosicaoExcursaoDTO> consultar(@PathVariable Long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(id,access.uid()));}
    @GetMapping(value="/execucoes-excursao/{id}/localizacao/eventos",produces="text/event-stream") public SseEmitter eventos(@PathVariable Long id,HttpServletRequest request,HttpServletResponse response){
        String uid=access.uid();cabecalhos(response);return streams.abrirContexto("U:"+uid,(Instant)request.getAttribute("firebaseExpiresAt"),()->{var p=service.consultar(id,uid);return new PosicaoStreamService.Atualizacao(p.estado(),p);});
    }
    @GetMapping("/publico/excursoes/operacao/{id}/localizacao") public ResponseEntity<PosicaoExcursaoDTO> participante(@PathVariable Long id,@RequestHeader(value="X-Inscricao",required=false)String token){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultarParticipante(id,token));}
    @GetMapping(value="/publico/excursoes/operacao/{id}/localizacao/eventos",produces="text/event-stream") public SseEmitter eventosParticipante(@PathVariable Long id,@RequestHeader(value="X-Inscricao",required=false)String token,HttpServletResponse response){
        var a=service.autorizar(id,token);cabecalhos(response);
        return streams.abrirContexto("C:"+a.credencialId(),a.expira(),()->{var p=service.consultarCredencial(id,a.credencialId());return new PosicaoStreamService.Atualizacao(p.estado(),p);});
    }
    private void cabecalhos(HttpServletResponse r){r.setHeader("Cache-Control","no-store");r.setHeader("X-Accel-Buffering","no");}
}
