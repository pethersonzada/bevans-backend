package com.bevans.dto;

import com.bevans.model.EstadoExcursao;
import com.bevans.model.StatusInscricaoExcursao;
import java.time.Instant;

public record ExcursaoPassageiroDTO(
    Long inscricaoId,
    Long excursaoId,
    String titulo,
    String destinoNome,
    Instant partidaEm,
    Instant retornoEm,
    EstadoExcursao estadoExcursao,
    StatusInscricaoExcursao estadoInscricao
) {}
