package com.bevans.dto;
import com.bevans.model.ModoColetaExcursao;
import java.util.List;
public record ColetaExcursaoRequest(ModoColetaExcursao modo,List<ParadaExcursaoRequest> paradas,Long versao) {}
