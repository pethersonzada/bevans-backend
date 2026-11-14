package com.bevans.service;
/** Adaptador de evidência confiável do servidor. Nunca receber prova ou flag de aprovação do cliente. */
public interface VerificacaoResponsabilidade {
    record Resultado(boolean comprovada,String referenciaServidor){}
    Resultado verificar(String responsavelUid,String passageiroUid);
}
