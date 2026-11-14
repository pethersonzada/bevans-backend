package com.bevans.dto;
import com.bevans.model.ModoColetaExcursao;
import java.time.Instant;
import java.util.List;
/** Sem organizador, participantes, origem operacional privada ou pins residenciais. */
public record ExcursaoConvitePublicoDTO(String titulo,String destinoNome,double destinoLatitude,double destinoLongitude,
        Instant partidaEm,Instant retornoEm,ModoColetaExcursao modoColeta,List<ParadaExcursaoDTO> paradas) {}
