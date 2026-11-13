package com.bevans.dto;
import com.bevans.model.*;
import java.time.*;
import java.util.*;
public record RotaPublicaDTO(Long id, Long turmaId, String nome, String cidade, CategoriaRota categoria,
        ModoRota modo, String origemNome, Double origemLatitude, Double origemLongitude,
        String destinoNome, Double destinoLatitude, Double destinoLongitude, String descricao,
        String regiao, String campus, Set<DayOfWeek> dias, LocalTime horarioIda, LocalTime horarioVolta,
        boolean publicada, boolean ativa, VagasTurmaDTO vagas, List<ParadaPublicaDTO> paradas, boolean favorita, boolean permitePontoExcepcional) {
    public RotaPublicaDTO comFavorita(boolean valor) {
        return new RotaPublicaDTO(id,turmaId,nome,cidade,categoria,modo,origemNome,origemLatitude,origemLongitude,
                destinoNome,destinoLatitude,destinoLongitude,descricao,regiao,campus,dias,horarioIda,horarioVolta,
                publicada,ativa,vagas,paradas,valor,permitePontoExcepcional);
    }
}
