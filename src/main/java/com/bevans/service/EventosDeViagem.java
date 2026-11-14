package com.bevans.service;
public final class EventosDeViagem {
    private EventosDeViagem(){}
    public record Viagem(String tipo,Long id){}
    public record Excursao(String tipo,Long id){}
    public record Operacao(Long operacaoId,Long registroId,String tipo,String chave){}
    public record Direto(String tipo,String contextoTipo,Long contextoId,Long usuarioId,Long inscricaoId,String chave){}
}
