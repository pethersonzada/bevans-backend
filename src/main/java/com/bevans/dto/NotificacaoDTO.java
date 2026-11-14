package com.bevans.dto;
import java.time.Instant;
public record NotificacaoDTO(Long id,String tipo,String contextoTipo,Long contextoId,String titulo,String mensagem,Instant criadaEm,Instant lidaEm){
    public record Preferencias(boolean pushOperacional,boolean pushInformativo){}
    public record Dispositivo(String instalacaoId,String token,Boolean consentePush){@Override public String toString(){return "Dispositivo[segredo protegido]";}}
    public record DispositivoInfo(Long id,String instalacaoId,boolean ativo,Instant atualizadoEm){}
}
