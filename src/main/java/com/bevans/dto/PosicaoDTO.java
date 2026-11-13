package com.bevans.dto;
import java.time.Instant;
public record PosicaoDTO(Long viagemId,String estado,Double latitude,Double longitude,Instant observadoEm,Instant recebidoEm,Double precisao,Double velocidade,int consultarAposSegundos) {}
