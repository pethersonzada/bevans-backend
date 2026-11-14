package com.bevans.controller;
import com.bevans.dto.NotificacaoDTO;
import com.bevans.service.NotificacaoInscricaoService;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/publico/excursoes/inscricao/notificacoes")
public class NotificacaoInscricaoController {
    private final NotificacaoInscricaoService service;
    public NotificacaoInscricaoController(NotificacaoInscricaoService s){service=s;}
    @GetMapping public ResponseEntity<Page<NotificacaoDTO>> listar(@RequestHeader(value="X-Inscricao",required=false)String token,@RequestParam(defaultValue="0")int pagina,@RequestParam(defaultValue="20")int tamanho){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.listar(token,pagina,tamanho));}
    @PutMapping("/{id}/leitura") public void ler(@RequestHeader(value="X-Inscricao",required=false)String token,@PathVariable Long id){service.ler(token,id);}
}
