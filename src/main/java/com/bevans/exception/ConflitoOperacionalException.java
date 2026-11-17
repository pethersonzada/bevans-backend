package com.bevans.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ConflitoOperacionalException extends ResponseStatusException {
    private final String codigo;
    public ConflitoOperacionalException(String codigo,String mensagem){super(HttpStatus.CONFLICT,mensagem);this.codigo=codigo;}
    public String getCodigo(){return codigo;}
}
