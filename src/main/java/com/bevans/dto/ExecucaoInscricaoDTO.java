package com.bevans.dto;

import com.bevans.model.EstadoExecucaoExcursao;
import java.time.Instant;

/** Identificação mínima da execução que contém a própria inscrição. */
public record ExecucaoInscricaoDTO(
        Long id, String sentido, EstadoExecucaoExcursao estado, Instant iniciadaEm
) {}
