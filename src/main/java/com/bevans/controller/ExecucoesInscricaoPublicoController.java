package com.bevans.controller;

import com.bevans.dto.ExecucaoInscricaoDTO;
import com.bevans.service.OperacaoService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Descoberta exclusiva das execuções registradas para a inscrição autorizada. */
@RestController
@RequestMapping("/publico/excursoes/inscricao/execucoes")
public class ExecucoesInscricaoPublicoController {
    private final OperacaoService service;

    public ExecucoesInscricaoPublicoController(OperacaoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ExecucaoInscricaoDTO>> minhas(
            @RequestHeader(name = "X-Inscricao", required = false) String credencial) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(service.execucoesWeb(credencial));
    }
}
