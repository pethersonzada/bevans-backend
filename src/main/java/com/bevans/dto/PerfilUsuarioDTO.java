package com.bevans.dto;
import com.bevans.model.Usuario;
/** Perfil completo exclusivamente em endpoints próprios, nunca como relacionamento aninhado. */
public record PerfilUsuarioDTO(Long id,String nome,String telefone,String email,String firebaseUid,String tipo,String enderecoCompleto,Double latitude,Double longitude){
    public static PerfilUsuarioDTO proprio(Usuario u){return new PerfilUsuarioDTO(u.getId(),u.getNome(),u.getTelefone(),u.getEmail(),u.getFirebaseUid(),u.getTipo(),u.getEnderecoCompleto(),u.getLatitude(),u.getLongitude());}
    @Override public String toString(){return "PerfilUsuarioDTO[dados privados]";}
}
