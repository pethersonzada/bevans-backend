package com.bevans.dto;
import com.bevans.model.CategoriaRota;
import java.time.DayOfWeek;
public record FiltroCatalogo(String q,String cidade,String destino,String campus,String regiao,String turno,
        CategoriaRota categoria,DayOfWeek dia,boolean somenteComVagas) {}
