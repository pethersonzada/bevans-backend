package com.bevans.dto;
import com.bevans.model.ModoColetaPrivada;
import java.time.LocalTime;
import java.util.List;
public record ColetaPrivadaDTO(Long turmaId,String sentido,ModoColetaPrivada modo,long versao,List<Parada> paradas,Long escolhidaId,Sugestao sugestao,Long viagemId,PosicaoDTO gps){
    public record Parada(Long id,String nome,double latitude,double longitude,int ordem,LocalTime horario){}
    public record Sugestao(Parada parada,double distanciaMetros,String metodo){}
}
