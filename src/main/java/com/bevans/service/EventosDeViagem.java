package com.bevans.service;
public final class EventosDeViagem {
    private EventosDeViagem(){}
    public record Viagem(String tipo,Long id){}
    public record Excursao(String tipo,Long id){}
    public record Operacao(Long operacaoId,Long registroId,String tipo,String chave){}
}
