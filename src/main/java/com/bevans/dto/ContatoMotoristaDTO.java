package com.bevans.dto;
import java.time.Instant;
public record ContatoMotoristaDTO(Long motoristaId,String nome,String telefoneComercial,String waMe){
    public record Configurar(String telefone,Boolean habilitado,Boolean aceiteDivulgacao,Long versao){@Override public String toString(){return "ConfigurarContato[dados privados]";}}
    public record Configuracao(String telefone,boolean habilitado,Instant consentimentoEm,long versao){@Override public String toString(){return "ConfiguracaoContato[dados privados]";}}
    @Override public String toString(){return "ContatoMotoristaDTO[dados protegidos]";}
}
