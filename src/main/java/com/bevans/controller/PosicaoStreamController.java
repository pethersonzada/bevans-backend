package com.bevans.controller;

import com.bevans.security.AccessPolicy;
import com.bevans.service.PosicaoStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class PosicaoStreamController {
    private final PosicaoStreamService streams;
    private final AccessPolicy access;
    public PosicaoStreamController(PosicaoStreamService streams,AccessPolicy access){this.streams=streams;this.access=access;}
    @GetMapping(value="/viagens/{id}/localizacao/eventos",produces="text/event-stream")
    public SseEmitter conectar(@PathVariable Long id,HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Accel-Buffering","no");
        return streams.abrir(id,access.uid(),(Instant)request.getAttribute("firebaseExpiresAt"));
    }
}
