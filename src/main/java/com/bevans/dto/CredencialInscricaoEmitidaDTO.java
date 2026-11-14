package com.bevans.dto;
import java.time.Instant;
public record CredencialInscricaoEmitidaDTO(String token,Instant expiraEm){
    @Override public String toString(){return "CredencialInscricaoEmitidaDTO[credencial protegida]";}
}
