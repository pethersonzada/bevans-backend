package com.bevans.controller;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.bevans.service.AutenticacaoPerfilService;
import com.bevans.security.AccessPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "4. Autenticação", description = "Segurança e acesso ao sistema")
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AccessPolicy access;
    private final AutenticacaoPerfilService perfis;

    public AuthController(AccessPolicy access,AutenticacaoPerfilService perfis){this.access=access;this.perfis=perfis;}

    @Operation(summary = "Login vinculado à identidade Firebase verificada")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.bevans.dto.ContratosEntrada.Login.class)))
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Map<String, String> payload,
                                        HttpServletRequest request) {
        return ResponseEntity.ok(perfis.login(access.uid(),payload==null?null:payload.get("email"),
            (String)request.getAttribute("firebaseEmail"),Boolean.TRUE.equals(request.getAttribute("firebaseEmailVerified"))));
    }
}
