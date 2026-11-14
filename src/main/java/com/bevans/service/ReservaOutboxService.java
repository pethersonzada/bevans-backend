package com.bevans.service;
import com.bevans.repository.OutboxNotificacaoRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ReservaOutboxService {
    private final OutboxNotificacaoRepository outbox;private final Clock clock;private final EntityManager em;
    public ReservaOutboxService(OutboxNotificacaoRepository o,Clock c,EntityManager e){outbox=o;clock=c;em=e;}
    @Transactional public Long reservar(){
        var lista=outbox.pendentes(clock.instant(),PageRequest.of(0,1));if(lista.isEmpty())return null;
        var o=lista.getFirst();em.refresh(o);
        if(!Set.of("PENDENTE","PROCESSANDO").contains(o.estado)||o.proximaTentativa.isAfter(clock.instant()))return null;
        if(o.tentativas>=5){o.estado="FALHA";return null;}
        o.estado="PROCESSANDO";o.tentativas++;o.proximaTentativa=clock.instant().plusSeconds(120);return o.id;
    }
    @Transactional public void falhar(Long id){
        var o=outbox.lockById(id).orElseThrow();em.refresh(o);if(!"PROCESSANDO".equals(o.estado))return;
        o.estado=o.tentativas>=5?"FALHA":"PENDENTE";o.proximaTentativa=clock.instant().plusSeconds(30L*(1L<<o.tentativas));
    }
}
