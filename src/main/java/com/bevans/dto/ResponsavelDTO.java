package com.bevans.dto;
import com.bevans.model.EstadoResponsabilidade;
import java.time.Instant;
public record ResponsavelDTO(Long id,Long passageiroId,Long responsavelId,EstadoResponsabilidade estado,long versao,
        Instant consentimentoPassageiroEm,Instant consentimentoResponsavelEm,String termoPassageiro,String termoResponsavel,
        Instant verificadoEm,Instant revogadoEm){
    public record Convite(String token,Instant expiraEm){@Override public String toString(){return "Convite[segredo protegido]";}}
    public record Solicitar(String token,Boolean aceite,String termoVersao){@Override public String toString(){return "Solicitar[segredo protegido]";}}
    public record Aprovar(Long versao,Boolean aceite,String termoVersao){}
    public record Revogar(Long versao){}
    public record Evento(String acao,EstadoResponsabilidade estado,Long autorId,Instant criadoEm,long versao,String termoVersao){}
}
