package com.bevans.service;
import com.bevans.repository.OperacaoViagemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Executado apenas após nova amostra válida, na transação sob lock do contexto de origem. */
@Service
public class ProximidadeService {
    private final OperacaoViagemRepository operacoes;private final ContextoProximidadeService contexto;private final ApplicationEventPublisher eventos;
    private final com.bevans.repository.OutboxNotificacaoRepository outbox;
    public ProximidadeService(OperacaoViagemRepository o,ContextoProximidadeService c,ApplicationEventPublisher e,com.bevans.repository.OutboxNotificacaoRepository outbox){operacoes=o;contexto=c;eventos=e;this.outbox=outbox;}
    @EventListener @Transactional public void atualizado(EventosDeViagem.Gps e){
        var op=operacoes.findByTipoAndReferenciaId(e.tipo(),e.id()).orElse(null);if(op==null)return;
        var alertas=contexto.alertas(op.id);if(alertas.isEmpty())return;
        var existentes=outbox.chavesExistentes(alertas.entrySet().stream().map(a->chave(op.id,a.getKey(),a.getValue())).toList());
        alertas.forEach((registro,tipo)->{String chave=chave(op.id,registro,tipo);if(!existentes.contains(chave))eventos.publishEvent(new EventosDeViagem.Operacao(op.id,registro,tipo,chave));});
    }
    private String chave(Long operacao,Long registro,String tipo){return "PROXIMIDADE:"+operacao+":"+registro+":"+tipo;}
}
