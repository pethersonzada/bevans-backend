package com.bevans.dto;
/** Retorno único: não registrar token em logs. */
public record ConviteEmitidoDTO(ConviteExcursaoDTO convite,String token) {
    @Override public String toString(){return "ConviteEmitidoDTO[credencial protegida]";}
}
