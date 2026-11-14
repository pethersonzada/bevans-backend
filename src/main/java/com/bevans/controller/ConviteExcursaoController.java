package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ConviteExcursaoService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/excursoes/{id}/convites") @PreAuthorize("@access.isMotorista()")
public class ConviteExcursaoController {
    private final ConviteExcursaoService service;private final AccessPolicy access;
    public ConviteExcursaoController(ConviteExcursaoService s,AccessPolicy a){service=s;access=a;}
    public record EmitirRequest(Instant expiraEm){}
    @PostMapping public ResponseEntity<ConviteEmitidoDTO> emitir(@PathVariable Long id,@RequestBody EmitirRequest d){
        if(d==null)throw new IllegalArgumentException("Informe validade.");
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.emitir(id,access.uid(),d.expiraEm()));
    }
    @GetMapping public List<ConviteExcursaoDTO> listar(@PathVariable Long id){return service.listar(id,access.uid());}
    @DeleteMapping("/{conviteId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revogar(@PathVariable Long id,@PathVariable Long conviteId){service.revogar(id,conviteId,access.uid());}
}
