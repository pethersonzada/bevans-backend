package com.bevans.dto;
import com.bevans.model.*;
import java.time.Instant;
import java.util.List;
public record OperacaoDTO(Long id,TipoOperacao tipo,Long referenciaId,String sentido,Contadores contadores,List<Registro> registros){
    public record Contadores(long esperados,long aguardando,long naVan,long naoEmbarcaram,long desembarcados){}
    public record Registro(Long id,Long alunoId,Long inscricaoId,String nome,EstadoOperacional estado,long versao,String autor,String origem,Instant atualizadoEm,Long paradaId,int ordem){@Override public String toString(){return "Registro[dados privados]";}}
    public record Evento(Long id,EstadoOperacional anterior,EstadoOperacional novo,String autor,String origem,Instant criadoEm,boolean correcao,String motivo,long versaoRegistro){@Override public String toString(){return "Evento[auditoria privada]";}}
    public record Alteracao(EstadoOperacional estado,Boolean correcao,Long versao,String motivo){}
    @Override public String toString(){return "OperacaoDTO[dados privados]";}
}
