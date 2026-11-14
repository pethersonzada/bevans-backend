package com.bevans.dto;
public record InscricaoExcursaoRequest(String nome,String contato,Long paradaId,String nomePonto,Double latitude,Double longitude,Boolean pontoConfirmado,Boolean aceitePrivacidade){
    @Override public String toString(){return "InscricaoExcursaoRequest[dados privados]";}
}
