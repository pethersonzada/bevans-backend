package com.bevans.service;
import org.springframework.stereotype.Component;
/** Nenhum serviço de comprovação foi aprovado/configurado; nenhuma liberação implícita de dados. */
@Component
public class VerificacaoResponsabilidadePendente implements VerificacaoResponsabilidade {
    public Resultado verificar(String responsavelUid,String passageiroUid){return new Resultado(false,null);}
}
