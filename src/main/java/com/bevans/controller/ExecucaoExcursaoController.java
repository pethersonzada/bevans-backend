package com.bevans.controller;
import com.bevans.dto.ExecucaoExcursaoDTO;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ExecucaoExcursaoService;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/excursoes/{id}/execucoes") @PreAuthorize("@access.isMotorista()")
public class ExecucaoExcursaoController {
    private final ExecucaoExcursaoService service;private final AccessPolicy access;
    public ExecucaoExcursaoController(ExecucaoExcursaoService s,AccessPolicy a){service=s;access=a;}
    @PostMapping public ResponseEntity<ExecucaoExcursaoDTO> iniciar(@PathVariable Long id,@RequestParam(defaultValue="IDA")String sentido,@RequestParam(defaultValue="false")boolean otimizar){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.iniciar(id,access.uid(),sentido,otimizar));}
    @GetMapping public ResponseEntity<List<ExecucaoExcursaoDTO>> listar(@PathVariable Long id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(id,access.uid()));}
    @PutMapping("/{execucaoId}/finalizacao") public ResponseEntity<ExecucaoExcursaoDTO> finalizar(@PathVariable Long id,@PathVariable Long execucaoId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.finalizar(id,execucaoId,access.uid()));}
}
