package com.bevans.dto;
import java.time.Instant;
public record ConviteExcursaoDTO(Long id,Instant expiraEm,Instant criadoEm,Instant revogadoEm) {}
