package com.bevans.dto;
import java.time.LocalDate;
public record EmbarqueRequest(LocalDate dataOperacao, String sentido, Long paradaId,
        String nomePonto, Double latitude, Double longitude, Boolean pontoConfirmado, String observacao) {}
