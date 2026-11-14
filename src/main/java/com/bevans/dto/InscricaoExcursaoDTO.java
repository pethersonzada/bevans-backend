package com.bevans.dto;
import com.bevans.model.StatusInscricaoExcursao;
import java.time.Instant;
public record InscricaoExcursaoDTO(Long id,String nome,StatusInscricaoExcursao estado,Long paradaId,String nomePonto,double latitude,double longitude,Instant reservaValidaAte,Instant confirmadaEm) {
    @Override public String toString(){return "InscricaoExcursaoDTO[dados privados]";}
}
