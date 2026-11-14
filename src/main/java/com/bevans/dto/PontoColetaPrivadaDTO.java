package com.bevans.dto;
import java.util.List;
public record PontoColetaPrivadaDTO(String tipo,Long paradaId,String nome,double latitude,double longitude,List<PassageiroRotaDTO> passageiros){
    @Override public String toString(){return "PontoColetaPrivadaDTO[dados privados]";}
}
