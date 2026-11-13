package com.bevans.controller;

import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.RotaPublicaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/rotas-publicas")
@Tag(name="Rotas Públicas urbanas",description="Publicação explícita e paradas; independente de Excursões e das turmas privadas.")
public class RotaPublicaController {
    private final RotaPublicaService service;
    private final AccessPolicy access;
    private final com.bevans.service.FavoritoService favoritos;
    public RotaPublicaController(RotaPublicaService service,AccessPolicy access,com.bevans.service.FavoritoService favoritos){this.service=service;this.access=access;this.favoritos=favoritos;}

    @PostMapping @PreAuthorize("@access.isMotorista()")
    @Operation(summary="Criar configuração privada de rota urbana",description="Não publica automaticamente; turmaId deve pertencer ao motorista autenticado.")
    public RotaPublicaDTO criar(@RequestBody RotaPublicaRequest dados){return service.criar(access.usuario().getId(),dados);}

    @GetMapping("/minhas") @PreAuthorize("@access.isMotorista()")
    public List<RotaPublicaDTO> minhas(){return service.minhas(access.usuario().getId());}

    @GetMapping("/{id}") @PreAuthorize("@rotasPublicasAccess.canRead(#p0)")
    @Operation(summary="Detalhes permitidos da rota",description="Sem telefones, passageiros, endereços privados da turma ou GPS.")
    public RotaPublicaDTO consultar(@PathVariable Long id){
        var usuario=access.usuario();return service.consultar(id).comFavorita(favoritos.favorita(usuario==null?null:usuario.getId(),id));
    }

    @PutMapping("/{id}") @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    public RotaPublicaDTO atualizar(@PathVariable Long id,@RequestBody RotaPublicaRequest dados){return service.atualizar(id,access.usuario().getId(),dados);}

    public record EstadoRequest(Boolean valor) {}
    private boolean estado(EstadoRequest d){if(d==null||d.valor()==null)throw new IllegalArgumentException("Informe valor true ou false.");return d.valor();}

    @PutMapping("/{id}/publicacao") @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    @Operation(summary="Publicar ou retirar do catálogo",description="Body {valor:true/false}; exige capacidade configurada e rota ativa; bloqueado durante viagem.")
    public RotaPublicaDTO publicar(@PathVariable Long id,@RequestBody EstadoRequest dados){return service.publicar(id,access.usuario().getId(),estado(dados));}

    @PutMapping("/{id}/disponibilidade") @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    public RotaPublicaDTO disponibilidade(@PathVariable Long id,@RequestBody EstadoRequest dados){return service.disponibilidade(id,access.usuario().getId(),estado(dados));}

    @PutMapping("/{id}/paradas") @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    @Operation(summary="Salvar e reordenar paradas",description="Array na ordem desejada. ID omitido cria parada; ID existente renomeia/reposiciona; omitida fica inativa. IDs de outra rota rejeitados. Limite configurável, padrão 50.")
    public RotaPublicaDTO paradas(@PathVariable Long id,@RequestBody List<ParadaPublicaDTO> dados){return service.salvarParadas(id,access.usuario().getId(),dados);}

    @PostMapping("/{id}/solicitacoes") @PreAuthorize("@access.isPassageiro()")
    @Operation(summary="Solicitar vaga na rota publicada",description="Não exige conhecer convite privado. Identidade vem do token; pedido continua pendente até aprovação.")
    public SolicitacaoVagaDTO solicitar(@PathVariable Long id){return service.solicitar(id,access.usuario().getId());}

    @PostMapping("/{id}/planejamento") @PreAuthorize("@rotasPublicasAccess.own(#p0)")
    @Operation(summary="Prévia de ordem antes da partida",description="FIXA respeita ordem cadastrada, VOLTA inverte. FLEXIVEL pode otimizar via OR-Tools sem gravar nova ordem. Nunca inclui pedidos pendentes.")
    public List<PontoPublicoDTO> planejamento(@PathVariable Long id,@RequestParam String sentido,@RequestParam(defaultValue="false") boolean otimizar) {
        return service.planejamento(id,access.usuario().getId(),sentido,otimizar);
    }
}
