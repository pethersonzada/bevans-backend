package com.bevans.dto;
import java.time.LocalTime;
public record ParadaExcursaoRequest(Long id,String nome,Double latitude,Double longitude,LocalTime horario,String instrucoes,Boolean pontoConfirmado) {}
