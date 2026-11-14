package com.bevans.controller;
import com.bevans.dto.PontoRoteiroExcursaoDTO;
import com.bevans.security.AccessPolicy;
import com.bevans.service.RoteiroExcursaoService;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @PreAuthorize("@access.isMotorista()")
public class RoteiroExcursaoController {
    private final RoteiroExcursaoService service;private final AccessPolicy access;
    public RoteiroExcursaoController(RoteiroExcursaoService s,AccessPolicy a){service=s;access=a;}
    @PostMapping("/excursoes/{id}/planejamento")
    public ResponseEntity<List<PontoRoteiroExcursaoDTO>> planejar(@PathVariable Long id,@RequestParam(defaultValue="IDA")String sentido,@RequestParam(defaultValue="false")boolean otimizar){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.planejamento(id,access.uid(),sentido,otimizar));
    }
}
