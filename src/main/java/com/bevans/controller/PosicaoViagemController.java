package com.bevans.controller;

import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.PosicaoViagemService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/viagens")
public class PosicaoViagemController {
    private final PosicaoViagemService service;
    private final AccessPolicy access;
    public PosicaoViagemController(PosicaoViagemService service,AccessPolicy access){this.service=service;this.access=access;}
    @PutMapping("/{id}/localizacao")
    public ResponseEntity<PosicaoDTO> enviar(@PathVariable Long id,@RequestBody PosicaoRequest d){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.enviar(id,access.uid(),d));
    }
    @GetMapping("/{id}/localizacao")
    public ResponseEntity<PosicaoDTO> consultar(@PathVariable Long id){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(id,access.uid()));
    }
}
