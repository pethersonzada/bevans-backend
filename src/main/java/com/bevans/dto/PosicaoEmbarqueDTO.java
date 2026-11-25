package com.bevans.dto;

import com.bevans.model.EstadoOperacional;

/** Somente dados do passageiro autenticado; não contém listas, nomes ou coordenadas de terceiros. */
public record PosicaoEmbarqueDTO(
        Long viagemId,
        EstadoOperacional estado,
        int ordem,
        Long paradasAntes,
        boolean proximo
) {}
