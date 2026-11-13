package com.bevans.dto;
import java.time.Instant;
public record PosicaoRequest(String amostraId,Double latitude,Double longitude,Instant observadoEm,Double precisao,Double velocidade) {}
