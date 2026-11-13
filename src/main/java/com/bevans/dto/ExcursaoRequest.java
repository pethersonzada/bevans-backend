package com.bevans.dto;
import java.time.Instant;
public record ExcursaoRequest(String titulo,String destinoNome,Double destinoLatitude,Double destinoLongitude,
        String origemNome,Double origemLatitude,Double origemLongitude,Instant partidaEm,Instant retornoEm,Integer capacidade,Long versao) {}
