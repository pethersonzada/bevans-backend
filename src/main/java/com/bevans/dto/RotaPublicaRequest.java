package com.bevans.dto;
import com.bevans.model.CategoriaRota;
import com.bevans.model.ModoRota;
import java.time.*;
import java.util.Set;

public record RotaPublicaRequest(Long turmaId, String nome, String cidade, CategoriaRota categoria,
        ModoRota modo, String origemNome, Double origemLatitude, Double origemLongitude,
        String destinoNome, Double destinoLatitude, Double destinoLongitude, String descricao,
        String regiao, String campus, Set<DayOfWeek> dias, LocalTime horarioIda, LocalTime horarioVolta) {}
