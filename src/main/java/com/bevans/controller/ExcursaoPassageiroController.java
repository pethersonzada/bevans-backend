package com.bevans.controller;

import com.bevans.dto.ExcursaoPassageiroDTO;
import com.bevans.dto.PaginaDTO;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ExcursaoPassageiroService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/passageiro/excursoes")
@PreAuthorize("@access.isPassageiro()")
public class ExcursaoPassageiroController {
    private final ExcursaoPassageiroService service;
    private final AccessPolicy access;

    public ExcursaoPassageiroController(
            ExcursaoPassageiroService service,
            AccessPolicy access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/vincular")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vincular(
            @RequestHeader(name = "X-Inscricao", required = false)
            String token) {
        service.vincular(access.uid(), token);
    }

    @GetMapping("/{inscricaoId}")
    public ExcursaoPassageiroDTO consultar(
            @PathVariable Long inscricaoId) {
        return service.consultar(access.uid(), inscricaoId);
    }

    @GetMapping
    public PaginaDTO<ExcursaoPassageiroDTO> minhas(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return service.minhas(access.uid(), pagina, tamanho);
    }
}
