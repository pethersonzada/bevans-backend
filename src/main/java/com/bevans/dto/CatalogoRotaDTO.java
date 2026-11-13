package com.bevans.dto;
import com.bevans.model.*;
import java.time.*;
import java.util.List;
/** Resumo público sem endereços/coordenadas individuais, convites ou telefones. */
public record CatalogoRotaDTO(Long id,Long turmaId,String nome,String motoristaNome,String cidade,CategoriaRota categoria,
        ModoRota modo,String destino,String campus,String regiao,String turno,List<DayOfWeek> dias,
        LocalTime horarioIda,LocalTime horarioVolta,VagasTurmaDTO vagas) {}
