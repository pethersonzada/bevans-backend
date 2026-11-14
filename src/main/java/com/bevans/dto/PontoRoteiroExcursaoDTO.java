package com.bevans.dto;
import java.util.List;
/** Somente organizador; pins residenciais não são dados de catálogo/convite. */
public record PontoRoteiroExcursaoDTO(String tipo,Long paradaId,String nome,double latitude,double longitude,List<Participante> participantes){
    public record Participante(Long inscricaoId,String nome){}
    @Override public String toString(){return "PontoRoteiroExcursaoDTO[dados operacionais privados]";}
}
