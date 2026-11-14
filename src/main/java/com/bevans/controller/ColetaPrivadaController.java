package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ColetaPrivadaService;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/turmas/{id}/coleta/{sentido}")
public class ColetaPrivadaController {
    private final ColetaPrivadaService service;private final AccessPolicy access;
    public ColetaPrivadaController(ColetaPrivadaService s,AccessPolicy a){service=s;access=a;}
    @GetMapping public ResponseEntity<ColetaPrivadaDTO> consultar(@PathVariable Long id,@PathVariable String sentido,@RequestParam(required=false)Long enderecoId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.consultar(id,sentido,access.uid(),enderecoId));}
    @PutMapping public ColetaPrivadaDTO configurar(@PathVariable Long id,@PathVariable String sentido,@RequestBody ColetaPrivadaRequest d){return service.configurar(id,sentido,access.uid(),d);}
    public record EscolhaRequest(Long paradaId,Long versao){}
    @PutMapping("/minha-parada") public void escolher(@PathVariable Long id,@PathVariable String sentido,@RequestBody EscolhaRequest d){if(d==null)throw new IllegalArgumentException("Informe parada e versão.");service.escolher(id,sentido,access.uid(),null,d.paradaId(),d.versao());}
    @DeleteMapping("/minha-parada") public void remover(@PathVariable Long id,@PathVariable String sentido){service.removerEscolha(id,sentido,access.uid(),null);}
    @PutMapping("/passageiros/{alunoId}/parada") public void atribuir(@PathVariable Long id,@PathVariable String sentido,@PathVariable Long alunoId,@RequestBody EscolhaRequest d){if(d==null)throw new IllegalArgumentException("Informe parada e versão.");service.escolher(id,sentido,access.uid(),alunoId,d.paradaId(),d.versao());}
    @DeleteMapping("/passageiros/{alunoId}/parada") public void removerAtribuicao(@PathVariable Long id,@PathVariable String sentido,@PathVariable Long alunoId){service.removerEscolha(id,sentido,access.uid(),alunoId);}
    @PostMapping("/planejamento") public ResponseEntity<List<PontoColetaPrivadaDTO>> planejar(@PathVariable Long id,@PathVariable String sentido,@RequestParam(defaultValue="false")boolean otimizar){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.planejar(id,sentido,access.uid(),otimizar));}
}
