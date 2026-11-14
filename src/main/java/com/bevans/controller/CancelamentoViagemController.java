package com.bevans.controller;
import com.bevans.security.AccessPolicy;
import com.bevans.service.CancelamentoViagemService;
import org.springframework.web.bind.annotation.*;
@RestController
public class CancelamentoViagemController {
    private final CancelamentoViagemService service;private final AccessPolicy access;
    public CancelamentoViagemController(CancelamentoViagemService s,AccessPolicy a){service=s;access=a;}
    @PutMapping("/viagens/{id}/cancelamento") public void cancelar(@PathVariable Long id){service.cancelar(id,access.uid());}
}
