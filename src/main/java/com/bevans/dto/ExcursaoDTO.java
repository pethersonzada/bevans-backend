package com.bevans.dto;
import com.bevans.model.EstadoExcursao;
import java.time.Instant;
public record ExcursaoDTO(Long id,String titulo,String destinoNome,Double destinoLatitude,Double destinoLongitude,
        String origemNome,Double origemLatitude,Double origemLongitude,Instant partidaEm,Instant retornoEm,int capacidade,EstadoExcursao estado,long versao,Instant criadaEm,
        com.bevans.model.ModoColetaExcursao modoColeta,java.util.List<ParadaExcursaoDTO> paradas) {}
