package com.bevans.service;
public interface PushGateway {
    enum Resultado { ENVIADO,TOKEN_INVALIDO,TRANSITORIO,PERMANENTE,INDISPONIVEL }
    Resultado enviar(String token,Long notificacao,String titulo,String mensagem);
}
