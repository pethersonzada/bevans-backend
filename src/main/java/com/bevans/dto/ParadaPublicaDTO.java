package com.bevans.dto;
import java.time.LocalTime;
public record ParadaPublicaDTO(Long id, String nome, Double latitude, Double longitude, LocalTime horario) {}
