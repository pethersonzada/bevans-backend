package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.model.CategoriaRota;
import com.bevans.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.DayOfWeek;

@RestController
public class CatalogoController {
    private final CatalogoService service;
    private final com.bevans.security.AccessPolicy access;
    public CatalogoController(CatalogoService service,com.bevans.security.AccessPolicy access){this.service=service;this.access=access;}
    @GetMapping("/rotas-publicas/catalogo")
    @PreAuthorize("@access.usuario()!=null")
    @Operation(summary="Descobrir rotas urbanas publicadas",description="Cidade é de operação urbana. Busca ignora maiúsculas e acentos latinos. Ordem por ID, tamanho máximo 50. Sem GPS/telefones/endereço residencial.")
    public PaginaDTO<CatalogoRotaDTO> buscar(@RequestParam(required=false) String q,@RequestParam(required=false) String cidade,
            @RequestParam(required=false) String destino,@RequestParam(required=false) String campus,@RequestParam(required=false) String regiao,
            @RequestParam(required=false) String turno,@RequestParam(required=false) CategoriaRota categoria,@RequestParam(required=false) DayOfWeek dia,
            @RequestParam(defaultValue="false") boolean somenteComVagas,@RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="20") int tamanho,
            @RequestParam(defaultValue="false") boolean somenteFavoritas) {
        if(somenteFavoritas&&!access.isPassageiro())throw new org.springframework.security.access.AccessDeniedException("Favoritos disponíveis somente ao passageiro titular.");
        return service.buscar(new FiltroCatalogo(q,cidade,destino,campus,regiao,turno,categoria,dia,somenteComVagas),pagina,tamanho,access.usuario().getId(),somenteFavoritas);
    }
}
