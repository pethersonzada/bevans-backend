package com.bevans.service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Ativação explícita: não inicia processos externos durante testes ou migrações. */
@Component @ConditionalOnProperty(name="app.notifications.worker.enabled",havingValue="true")
public class NotificacaoWorker {
    private final NotificacaoService notificacoes;private final EntregaPushService entregas;private final PushGateway gateway;
    private final ReservaOutboxService reservas;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(NotificacaoWorker.class);
    public NotificacaoWorker(NotificacaoService n,EntregaPushService e,PushGateway g,ReservaOutboxService r){notificacoes=n;entregas=e;gateway=g;reservas=r;}
    @Scheduled(fixedDelayString="${app.notifications.interval-ms:5000}") public void executar(){
        for(int i=0;i<10;i++){
            Long id=reservas.reservar();if(id==null)break;
            try{notificacoes.distribuir(id);}catch(RuntimeException e){reservas.falhar(id);log.warn("Falha ao consumir evento de notificação {}; nova tentativa controlada",id);}
        }
        for(int i=0;i<10;i++){
            var pedido=entregas.reservar();if(pedido==null)break;
            PushGateway.Resultado resultado;
            try{resultado=gateway.enviar(pedido.token(),pedido.notificacao(),pedido.titulo(),pedido.mensagem());}catch(RuntimeException e){resultado=PushGateway.Resultado.TRANSITORIO;}
            entregas.concluir(pedido,resultado);
        }
    }
}
