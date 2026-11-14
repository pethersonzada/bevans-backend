package com.bevans.dto;
import java.time.LocalTime;
public record ParadaExcursaoDTO(Long id,int ordem,String nome,double latitude,double longitude,LocalTime horario,String instrucoes) {}
