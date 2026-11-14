package com.bevans.dto;
import com.bevans.model.ModoColetaPrivada;
import java.time.LocalTime;
import java.util.List;
public record ColetaPrivadaRequest(ModoColetaPrivada modo,Long versao,List<Parada> paradas){
    public record Parada(Long id,String nome,Double latitude,Double longitude,LocalTime horario,Boolean pontoConfirmado){}
}
