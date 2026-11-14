package com.bevans.service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="app.contas.exclusao-worker.enabled",havingValue="true")
public class ExclusaoContaWorker {
    private final ExclusaoContaService service;private final ExclusaoIdentidadeGateway gateway;
    public ExclusaoContaWorker(ExclusaoContaService s,ExclusaoIdentidadeGateway g){service=s;gateway=g;}
    @Scheduled(fixedDelay=10000) public void executar(){for(int i=0;i<5;i++){
        var p=service.reservar();if(p==null)break;ExclusaoIdentidadeGateway.Resultado resultado;
        try{resultado=gateway.excluir(p.uid());}catch(RuntimeException ex){resultado=ExclusaoIdentidadeGateway.Resultado.TRANSITORIO;}
        service.concluir(p,resultado);
    }}
}
