package com.bevans.controller;

import com.bevans.dto.SolicitacaoVagaDTO;
import com.bevans.security.AccessPolicy;
import com.bevans.service.TurmaService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/turmas/solicitacoes")
public class SolicitacaoController {
    private final TurmaService service;
    private final AccessPolicy access;
    public SolicitacaoController(TurmaService service, AccessPolicy access) { this.service=service; this.access=access; }

    @GetMapping
    @PreAuthorize("@access.isPassageiro()")
    @Operation(summary="Consultar minhas solicitações", description="Identidade vem do Firebase; não retorna dados de outros passageiros, endereços ou convites.")
    public List<SolicitacaoVagaDTO> minhas() { return service.minhasSolicitacoes(access.usuario().getId()); }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.ownVinculo(#p0)")
    @Operation(summary="Cancelar minha solicitação pendente", description="Cancelamento repetido é idempotente; aprovada/rejeitada retorna 409.")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        service.cancelarSolicitacao(id, access.usuario().getId());
        return ResponseEntity.noContent().build();
    }
}
