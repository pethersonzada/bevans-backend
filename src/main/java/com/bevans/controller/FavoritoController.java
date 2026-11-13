package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.FavoritoService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("@access.isPassageiro()")
public class FavoritoController {
    private final FavoritoService service;
    private final AccessPolicy access;
    public FavoritoController(FavoritoService service,AccessPolicy access){this.service=service;this.access=access;}
    @PutMapping("/rotas-publicas/{id}/favorito")
    @Operation(summary="Favoritar rota publicada",description="Idempotente, identidade da sessão; não aceita usuarioId do cliente.")
    public ResponseEntity<Void> favoritar(@PathVariable Long id){service.favoritar(access.usuario().getId(),id);return ResponseEntity.noContent().build();}
    @DeleteMapping("/rotas-publicas/{id}/favorito")
    public ResponseEntity<Void> remover(@PathVariable Long id){service.remover(access.usuario().getId(),id);return ResponseEntity.noContent().build();}
    @GetMapping("/rotas-publicas/favoritas")
    public PaginaDTO<CatalogoRotaDTO> listar(@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="20") int tamanho){return service.listar(access.usuario().getId(),pagina,tamanho);}
}
