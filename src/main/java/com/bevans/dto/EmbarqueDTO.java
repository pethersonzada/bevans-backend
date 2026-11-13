package com.bevans.dto;
import com.bevans.model.StatusEmbarque;
import java.time.*;
public record EmbarqueDTO(Long id, Long rotaId, Long alunoId, LocalDate dataOperacao, String sentido,
        Long paradaId, String nomePonto, double latitude, double longitude, String observacao,
        StatusEmbarque status, Instant criadoEm, Instant analisadoEm) {}
