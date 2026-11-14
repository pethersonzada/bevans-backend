package com.bevans.controller;
import com.bevans.dto.*;
import com.bevans.security.AccessPolicy;
import com.bevans.service.ResponsavelService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/responsaveis")
public class ResponsavelController {
    private final ResponsavelService service;private final AccessPolicy access;
    public ResponsavelController(ResponsavelService s,AccessPolicy a){service=s;access=a;}
    @PostMapping("/convites") public ResponsavelDTO.Convite emitir(){return service.emitirConvite(access.uid());}
    @DeleteMapping("/convites") public void revogarConvites(){service.revogarConvites(access.uid());}
    @PostMapping("/vinculos") public ResponsavelDTO solicitar(@RequestBody ResponsavelDTO.Solicitar d){return service.solicitar(access.uid(),d);}
    @GetMapping("/vinculos") public Page<ResponsavelDTO> listar(@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return service.listar(access.uid(),pagina,tamanho);}
    @PutMapping("/vinculos/{id}/aprovacao") public ResponsavelDTO aprovar(@PathVariable Long id,@RequestBody ResponsavelDTO.Aprovar d){return service.aprovar(id,access.uid(),d);}
    @PutMapping("/vinculos/{id}/revogacao") public ResponsavelDTO revogar(@PathVariable Long id,@RequestBody ResponsavelDTO.Revogar d){return service.revogar(id,access.uid(),d);}
    @GetMapping("/vinculos/{id}/historico") public Page<ResponsavelDTO.Evento> historico(@PathVariable Long id,@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return service.historico(id,access.uid(),pagina,tamanho);}
    @GetMapping("/vinculos/{id}/operacao") public OperacaoDTO.Registro operacao(@PathVariable Long id,@RequestParam Long viagemId){return service.operacao(id,viagemId,access.uid());}
    @GetMapping("/vinculos/{id}/localizacao") public PosicaoDTO localizacao(@PathVariable Long id,@RequestParam Long viagemId){return service.localizacao(id,viagemId,access.uid());}
}
