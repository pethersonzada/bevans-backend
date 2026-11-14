package com.bevans.controller;
import com.bevans.dto.NotificacaoDTO;
import com.bevans.security.AccessPolicy;
import com.bevans.service.NotificacaoService;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/notificacoes")
public class NotificacaoController {
    private final NotificacaoService service;private final AccessPolicy access;
    public NotificacaoController(NotificacaoService s,AccessPolicy a){service=s;access=a;}
    @GetMapping public ResponseEntity<Page<NotificacaoDTO>> listar(@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(access.uid(),pagina,tamanho));}
    @GetMapping("/nao-lidas") public long naoLidas(){return service.naoLidas(access.uid());}
    @PutMapping("/{id}/leitura") public void ler(@PathVariable Long id){service.ler(access.uid(),id);}
    @PutMapping("/leitura") public void lerTodas(){service.lerTodas(access.uid());}
}
