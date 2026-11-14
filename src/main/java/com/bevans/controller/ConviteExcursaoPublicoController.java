package com.bevans.controller;
import com.bevans.dto.ExcursaoConvitePublicoDTO;
import com.bevans.service.ConviteExcursaoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class ConviteExcursaoPublicoController {
    private final ConviteExcursaoService service;
    public ConviteExcursaoPublicoController(ConviteExcursaoService s){service=s;}
    @GetMapping("/publico/excursoes/convite")
    public ResponseEntity<ExcursaoConvitePublicoDTO> consultar(@RequestHeader(name="X-Convite",required=false)String token){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(service.consultar(token));
    }
}
