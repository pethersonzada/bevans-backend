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
    public ProximidadeService(OperacaoViagemRepository o,ContextoProximidadeService c,ApplicationEventPublisher e){operacoes=o;contexto=c;eventos=e;}
    @EventListener @Transactional public void atualizado(EventosDeViagem.Gps e){
        var op=operacoes.findByTipoAndReferenciaId(e.tipo(),e.id()).orElse(null);if(op==null)return;
        contexto.alertas(op.id).forEach((registro,tipo)->eventos.publishEvent(new EventosDeViagem.Operacao(op.id,registro,tipo,"PROXIMIDADE:"+op.id+":"+registro+":"+tipo)));
    }
}
