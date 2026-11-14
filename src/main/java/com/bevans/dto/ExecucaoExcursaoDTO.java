package com.bevans.dto;
import com.bevans.model.EstadoExecucaoExcursao;
import java.time.Instant;
import java.util.List;
public record ExecucaoExcursaoDTO(Long id,Long excursaoId,String sentido,EstadoExecucaoExcursao estado,Instant iniciadaEm,Instant finalizadaEm,long versao,List<PontoRoteiroExcursaoDTO> pontos){
    @Override public String toString(){return "ExecucaoExcursaoDTO[dados operacionais privados]";}
}
